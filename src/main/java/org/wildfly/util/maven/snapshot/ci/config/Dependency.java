package org.wildfly.util.maven.snapshot.ci.config;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface Dependency {
    String getName();

    String getProperty();
}