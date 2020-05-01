package org.wildfly.util.maven.snapshot.ci.config.component;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class JobRunElementConfig {
    private final Type type;
    private final String command;

    public JobRunElementConfig(Type type, String command) {
        this.type = type;
        this.command = command;
    }

    public Type getType() {
        return type;
    }

    public String getCommand() {
        return command;
    }

    public enum Type {
        MVN,
        SHELL
    }
}
