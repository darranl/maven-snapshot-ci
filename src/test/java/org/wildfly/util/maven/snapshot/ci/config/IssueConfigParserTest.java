package org.wildfly.util.maven.snapshot.ci.config;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.util.maven.snapshot.ci.config.issue.Component;
import org.wildfly.util.maven.snapshot.ci.config.issue.IssueConfig;
import org.wildfly.util.maven.snapshot.ci.config.issue.IssueConfigParser;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class IssueConfigParserTest {
    @Test
    public void testParseYaml() throws Exception {
        URL url = this.getClass().getResource("issue-test.yml");
        Path path = Paths.get(url.toURI());
        IssueConfig issueConfig = IssueConfigParser.create(path).parse();

        Assert.assertNotNull(issueConfig);

        Map<String, Object> env = issueConfig.getEnv();
        Assert.assertEquals(2, env.size());
        Assert.assertEquals("-Xms756M -Xmx1g", env.get("MAVEN_OPTS"));
        Assert.assertEquals("value1", env.get("VALUE1"));

        List<Component> components = issueConfig.getComponents();
        Assert.assertNotNull(components);
        Assert.assertEquals(2, components.size());

        Component wfCommon = components.get(0);
        Assert.assertEquals("wildfly-common", wfCommon.getName());
        Assert.assertEquals("wildfly", wfCommon.getOrg());
        Assert.assertEquals("master", wfCommon.getBranch());
        Assert.assertNull(wfCommon.getMavenOpts());
        Assert.assertNotNull(wfCommon.getDependencies());
        Assert.assertEquals(0, wfCommon.getDependencies().size());

        Component wfElytron = components.get(1);
        Assert.assertEquals("wildfly-elytron", wfElytron.getName());
        Assert.assertEquals("kabir", wfElytron.getOrg());
        Assert.assertEquals("feature", wfElytron.getBranch());
        Assert.assertEquals("-DskipTests -Dhello=true", wfElytron.getMavenOpts());
        Assert.assertNotNull(wfElytron.getDependencies());
        Assert.assertEquals(1, wfElytron.getDependencies().size());
        Assert.assertEquals("wildfly-common", wfElytron.getDependencies().get(0).getName());
        Assert.assertEquals("version.wildfly.common", wfElytron.getDependencies().get(0).getProperty());
    }
}
