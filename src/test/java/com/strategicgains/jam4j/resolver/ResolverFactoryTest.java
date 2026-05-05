package com.strategicgains.jam4j.resolver;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ResolverFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void configuresBoundedHttpTimeouts() {
        DefaultRepositorySystemSession session = ResolverFactory.newSession(
            ResolverFactory.newRepositorySystem(), tempDir);

        assertThat(session.getConfigProperties())
            .containsEntry(ConfigurationProperties.CONNECT_TIMEOUT, ResolverFactory.CONNECT_TIMEOUT_MILLIS)
            .containsEntry(ConfigurationProperties.REQUEST_TIMEOUT, ResolverFactory.REQUEST_TIMEOUT_MILLIS)
            .containsEntry(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, ResolverFactory.HTTP_RETRY_COUNT)
            .containsEntry(ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL, ResolverFactory.HTTP_RETRY_INTERVAL_MILLIS)
            .containsEntry(ConfigurationProperties.USER_AGENT, "jam4j");
        assertThat(session.isIgnoreArtifactDescriptorRepositories()).isFalse();
    }

    @Test
    void canIgnoreArtifactDescriptorRepositories() {
        DefaultRepositorySystemSession session = ResolverFactory.newSession(
            ResolverFactory.newRepositorySystem(), tempDir, true, null);

        assertThat(session.isIgnoreArtifactDescriptorRepositories()).isTrue();
    }
}
