package com.strategicgains.jam4j.resolver;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsInvalidCoordinate() {
        ArtifactResolver resolver = new ArtifactResolver(
            Path.of(System.getProperty("user.home"), ".m2", "repository"));
        assertThatThrownBy(() -> resolver.resolve(List.of("com.example:foo"), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("group:artifact:version");
    }

    @Test
    @Tag("integration")
    void resolvesCommonsLang3() throws Exception {
        ArtifactResolver resolver = new ArtifactResolver(tempDir);
        List<File> files = resolver.resolve(
            List.of("org.apache.commons:commons-lang3:3.14.0"), List.of());
        assertThat(files).isNotEmpty();
        assertThat(files).anyMatch(f -> f.getName().contains("commons-lang3"));
    }

    @Test
    @Tag("integration")
    void searchReturnsResults() throws Exception {
        ArtifactResolver resolver = new ArtifactResolver(tempDir);
        List<ArtifactResolver.SearchResult> results = resolver.search("jackson-databind", 5);
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r -> r.artifactId().equals("jackson-databind"));
    }
}
