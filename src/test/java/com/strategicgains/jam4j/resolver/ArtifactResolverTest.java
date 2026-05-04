package com.strategicgains.jam4j.resolver;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
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
    void localSearchExcludesSnapshotsByDefault() throws Exception {
        writeLocalArtifact("com.example", "demo", "1.0.0-SNAPSHOT");
        writeLocalArtifact("com.example", "demo", "1.0.0");

        ArtifactResolver resolver = new ArtifactResolver(tempDir);

        List<ArtifactResolver.SearchResult> results = resolver.search("demo",
            new ArtifactResolver.SearchOptions(10, false, true, false, List.of()));

        assertThat(results)
            .extracting(ArtifactResolver.SearchResult::coord)
            .containsExactly("com.example:demo:1.0.0");
    }

    @Test
    void localSearchIncludesSnapshotsWhenRequested() throws Exception {
        writeLocalArtifact("com.example", "demo", "1.0.0-SNAPSHOT");

        ArtifactResolver resolver = new ArtifactResolver(tempDir);

        List<ArtifactResolver.SearchResult> results = resolver.search("demo",
            new ArtifactResolver.SearchOptions(10, true, true, false, List.of()));

        assertThat(results)
            .extracting(ArtifactResolver.SearchResult::coord)
            .containsExactly("com.example:demo:1.0.0-SNAPSHOT");
    }

    @Test
    void localSearchMatchesGroupArtifactQuery() throws Exception {
        writeLocalArtifact("com.example", "demo", "1.0.0-SNAPSHOT");

        ArtifactResolver resolver = new ArtifactResolver(tempDir);

        List<ArtifactResolver.SearchResult> results = resolver.search("com.example:demo",
            new ArtifactResolver.SearchOptions(10, true, true, false, List.of()));

        assertThat(results)
            .extracting(ArtifactResolver.SearchResult::coord)
            .containsExactly("com.example:demo:1.0.0-SNAPSHOT");
    }

    @Test
    void parsesRepositoryMetadataVersions() throws Exception {
        List<String> versions = ArtifactResolver.parseMetadataVersions("""
            <metadata>
              <versioning>
                <versions>
                  <version>1.0.0</version>
                  <version>1.1.0-SNAPSHOT</version>
                </versions>
              </versioning>
            </metadata>
            """);

        assertThat(versions).containsExactly("1.0.0", "1.1.0-SNAPSHOT");
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

    private void writeLocalArtifact(String groupId, String artifactId, String version) throws Exception {
        Path versionDir = tempDir
            .resolve(groupId.replace('.', File.separatorChar))
            .resolve(artifactId)
            .resolve(version);
        Files.createDirectories(versionDir);
        Files.writeString(versionDir.resolve(artifactId + "-" + version + ".pom"), "<project/>");
    }
}
