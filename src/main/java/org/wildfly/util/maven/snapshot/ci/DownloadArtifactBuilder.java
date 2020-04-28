package org.wildfly.util.maven.snapshot.ci;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DownloadArtifactBuilder extends AbstractArtifactBuilder<DownloadArtifactBuilder> {
    public DownloadArtifactBuilder() {
        super("actions/download-artifact@v1");
    }

    @Override
    DownloadArtifactBuilder getThis() {
        return this;
    }
}
