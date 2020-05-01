package org.wildfly.util.maven.snapshot.ci;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.wildfly.util.maven.snapshot.ci.config.component.ComponentJobsConfig;
import org.wildfly.util.maven.snapshot.ci.config.component.ComponentJobsConfigParser;
import org.wildfly.util.maven.snapshot.ci.config.component.JobConfig;
import org.wildfly.util.maven.snapshot.ci.config.component.JobRunElementConfig;
import org.wildfly.util.maven.snapshot.ci.config.issue.Component;
import org.wildfly.util.maven.snapshot.ci.config.issue.IssueConfig;
import org.wildfly.util.maven.snapshot.ci.config.issue.IssueConfigParser;
import org.wildfly.util.maven.snapshot.ci.config.issue.Dependency;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class GitHubActionGenerator {
    static final String PROJECT_VERSIONS_DIRECTORY = ".project_versions";
    static final Path COMPONENT_JOBS_DIR = Paths.get(".repo-config/component-jobs");
    private final Map<String, Object> workflow = new LinkedHashMap<>();
    private final Map<String, ComponentJobsConfig> componentJobsConfigs = new HashMap<>();
    private final Path workflowFile;
    private final Path yamlConfig;
    private final String branchName;
    private final String issueNumber;

    private GitHubActionGenerator(Path workflowFile, Path yamlConfig, String branchName, String issueNumber) {
        this.workflowFile = workflowFile;
        this.yamlConfig = yamlConfig;
        this.branchName = branchName;
        this.issueNumber = issueNumber;
    }

    static GitHubActionGenerator create(Path workflowDir, Path yamlConfig, String branchName, String issueNumber) {
        Path workflowFile = workflowDir.resolve("ci-" + issueNumber + ".yml");
        return new GitHubActionGenerator(workflowFile, yamlConfig, branchName, issueNumber);
    }

    void generate() throws Exception {
        if (workflow.size() > 0) {
            throw new IllegalStateException("generate() called twice?");
        }
        IssueConfig issueConfig = IssueConfigParser.create(yamlConfig).parse();
        System.out.println("Wil create workflow file at " + workflowFile.toAbsolutePath());

        setupWorkFlowHeaderSection(issueConfig);
        setupJobs(issueConfig);

        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        // Fix below - additional configuration
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        String output = yaml.dump(workflow);
        System.out.println("-----------------");
        System.out.println(output);
        System.out.println("-----------------");
        Files.write(workflowFile, output.getBytes(StandardCharsets.UTF_8));
    }

    private void setupWorkFlowHeaderSection(IssueConfig issueConfig) {
        workflow.put("name", issueConfig.getName());
        workflow.put("on", Collections.singletonMap("push", Collections.singletonMap("branches", branchName)));

        if (issueConfig.getEnv().size() > 0) {
            Map<String, Object> env = new HashMap<>();
            for (String key : issueConfig.getEnv().keySet()) {
                env.put(key, issueConfig.getEnv().get(key));
            }
            workflow.put("env", env);
        }
    }

    private void setupJobs(IssueConfig issueConfig) throws Exception {
        Map<String, Object> componentJobs = new LinkedHashMap<>();

        for (Component component : issueConfig.getComponents()) {
            Path componentJobsFile = COMPONENT_JOBS_DIR.resolve(component.getName() + ".yml");
            if (!Files.exists(componentJobsFile)) {
                System.out.println("No " + componentJobsFile + " found");
                componentJobsFile = COMPONENT_JOBS_DIR.resolve(component.getName() + ".yaml");
            }
            if (!Files.exists(componentJobsFile)) {
                System.out.println("No " + componentJobsFile + " found. Setting up default job for component: " + component.getName());
                setupDefaultComponentBuildJob(componentJobs, component);
            } else {
                System.out.println("using " + componentJobsFile + " to add job(s) for component: " + component.getName());
                setupComponentBuildJobsFromFile(componentJobs, component, componentJobsFile);
            }
        }
        workflow.put("jobs", componentJobs);
    }

    private void setupDefaultComponentBuildJob(Map<String, Object> componentJobs, Component component) {
        DefaultComponentJobContext context = new DefaultComponentJobContext(component);
        Map<String, Object> job = setupJob(context);
        componentJobs.put(getComponentBuildId(component.getName()), job);
    }

    private void setupComponentBuildJobsFromFile(Map<String, Object> componentJobs, Component component, Path componentJobsFile) throws Exception {
        ComponentJobsConfig config = ComponentJobsConfigParser.create(componentJobsFile).parse();
        if (component.getMavenOpts() != null) {
            throw new IllegalStateException(component.getName() +
                    " defines mavenOpts but has a component job file at " + componentJobsFile +
                    ". Remove mavenOpts and configure the job in the compponent job file.");
        }
        componentJobsConfigs.put(component.getName(), config);
        List<JobConfig> jobConfigs = config.getJobs();
        for (JobConfig jobConfig : jobConfigs) {
            setupComponentBuildJobFromConfig(componentJobs, component, jobConfig);
        }
    }

    private void setupComponentBuildJobFromConfig(Map<String, Object> componentJobs, Component component, JobConfig jobConfig) {
        ConfiguredComponentJobContext context = new ConfiguredComponentJobContext(component, jobConfig);
        Map<String, Object> job = setupJob(context);
        componentJobs.put(jobConfig.getName(), job);
    }

    private Map<String, Object> setupJob(ComponentJobContext context) {
        Component component = context.getComponent();

        Map<String, Object> job = new LinkedHashMap<>();
        job.put("name", context.getJobName());
        job.put("runs-on", "ubuntu-latest");

        Map<String, String> env = context.createEnv();
        if (env.size() > 0) {
            job.put("env", env);
        }

        List<String> needs = context.createNeeds();
        if (needs.size() > 0) {
            job.put("needs", needs);
        }

        List<Object> steps = new ArrayList<>();
        steps.add(
                new CheckoutBuilder()
                        .setRepo(component.getOrg(), component.getName())
                        .setBranch(component.getBranch())
                        .build());
        steps.add(
                new CacheMavenRepoBuilder()
                        .build());
        steps.add(
                new SetupJavaBuilder()
                        .setVersion("11")
                        .build());

        for (Dependency dependency : component.getDependencies()) {
            steps.add(
                    new DownloadArtifactBuilder()
                            .setPath(PROJECT_VERSIONS_DIRECTORY)
                            .setName(getVersionArtifactName(dependency.getName()))
                            .build());
            steps.add(
                    new ReadFileIntoEnvVarBuilder()
                            .setPath(PROJECT_VERSIONS_DIRECTORY + "/" + dependency.getName())
                            .setEnvVarName(getVersionEnvVarName(dependency.getName()))
                            .build());
        }

        steps.add(
                new GrabProjectVersionBuilder()
                        .setFileName(PROJECT_VERSIONS_DIRECTORY, component.getName())
                        .build());
        steps.add(
                new UploadArtifactBuilder()
                        .setName(getVersionArtifactName(component.getName()))
                        .setPath(PROJECT_VERSIONS_DIRECTORY + "/" + component.getName())
                        .build());
        steps.addAll(context.createBuildSteps());

        job.put("steps", steps);

        return job;
    }

    private String getComponentBuildId(String name) {
        return name + "-build";
    }

    private String getVersionArtifactName(String name) {
        return "version-" + name;
    }

    private String getVersionEnvVarName(String name) {
        return "VERSION_" + name.replace("-", "_");
    }

    private abstract class ComponentJobContext {
        protected final Component component;

        public ComponentJobContext(Component component) {
            this.component = component;
        }

        public abstract Object getJobName();

        public Component getComponent() {
            return component;
        }

        protected List<String> createNeeds() {
            List<String> needs = new ArrayList<>();
            if (component.getDependencies().size() > 0) {
                for (Dependency dep : component.getDependencies()) {
                    String depComponentName = dep.getName();
                    ComponentJobsConfig componentJobsConfig = componentJobsConfigs.get(depComponentName);
                    if (componentJobsConfig == null) {
                        needs.add(getComponentBuildId(depComponentName));
                    } else {
                        List<String> exportedJobs = componentJobsConfig.getExportedJobs();
                        if (exportedJobs.size() == 0) {
                            throw new IllegalStateException(component.getName() + " has a 'needs' dependency on " +
                                    "the custom configured component '" + depComponentName + "', which is not exporting" +
                                    "any of its jobs to depend upon.");
                        }
                        needs.addAll(exportedJobs);
                    }
                }
            }
            return needs;
        }

        abstract List<Map<String, Object>> createBuildSteps();

        protected String getDependencyVersionMavenProperties() {
            StringBuilder sb = new StringBuilder();
            for (Dependency dep : component.getDependencies()) {
                sb.append(" ");
                sb.append("-D" + dep.getProperty() + "=\"${" + getVersionEnvVarName(dep.getName()) + "}\"");
            }
            return sb.toString();
        }

        public Map<String, String> createEnv() {
            return Collections.emptyMap();
        }
    }

    private class DefaultComponentJobContext extends ComponentJobContext {
        public DefaultComponentJobContext(Component component) {
            super(component);
        }

        @Override
        public Object getJobName() {
            return component.getName();
        }

        @Override
        List<Map<String, Object>> createBuildSteps() {
            return Collections.singletonList(
                    new MavenBuildBuilder()
                            .setOptions(getMavenOptions(component))
                            .build());
        }

        private String getMavenOptions(Component component) {
            StringBuilder sb = new StringBuilder();
            if (component.getMavenOpts() != null) {
                sb.append(component.getMavenOpts());
                sb.append(" ");
            }
            sb.append(getDependencyVersionMavenProperties());
            return sb.toString();
        }
    }

    private class ConfiguredComponentJobContext extends ComponentJobContext {
        private final JobConfig jobConfig;

        public ConfiguredComponentJobContext(Component component, JobConfig jobConfig) {
            super(component);
            this.jobConfig = jobConfig;
        }

        @Override
        public Object getJobName() {
            return jobConfig.getName();
        }

        @Override
        protected List<String> createNeeds() {
            List<String> needs = super.createNeeds();
            for (String need : jobConfig.getNeeds()) {
                needs.add(need);
            }
            return needs;
        }

        @Override
        List<Map<String, Object>> createBuildSteps() {
            List<JobRunElementConfig> runElementConfigs = jobConfig.getRunElements();
            Map<String, Object> build = new HashMap<>();
            build.put("name", "Maven Build");
            StringBuilder sb = new StringBuilder();
            for (JobRunElementConfig cfg : runElementConfigs) {
                if (cfg.getType() == JobRunElementConfig.Type.SHELL) {
                    sb.append(cfg.getCommand());
                    sb.append("\n");
                } else {
                    sb.append("mvn -B ");
                    sb.append(cfg.getCommand());
                    sb.append(" ");
                    sb.append(getDependencyVersionMavenProperties());
                    sb.append("\n");
                }
            }
            build.put("run", sb.toString());
            return Collections.singletonList(build);
        }

        @Override
        public Map<String, String> createEnv() {
            return jobConfig.getJobEnv();
        }
    }
}
