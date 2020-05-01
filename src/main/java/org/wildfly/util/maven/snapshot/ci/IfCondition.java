package org.wildfly.util.maven.snapshot.ci;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public enum  IfCondition {
    SUCCESS("${{ success() }}"),
    FAILURE("${{ failure() }}"),
    ALWAYS("${{ always() }}");

    private final String value;

    IfCondition(String value) {
        this.value = value;
    }

    String getValue() {
        return value;
    }
}
