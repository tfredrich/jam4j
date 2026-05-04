package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.model.ProjectJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstallCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void savesExplicitArtifactsByDefault() throws Exception {
        Path projectFile = tempDir.resolve("project.json");
        ProjectJson project = new ProjectJson();
        project.save(projectFile);

        InstallCommand command = new InstallCommand();
        command.projectFile = projectFile;

        boolean updated = command.saveArtifactsToProject(List.of(
            "com.strategicgains:RestExpress:0.12.0-SNAPSHOT"));

        ProjectJson updatedProject = ProjectJson.load(projectFile);
        assertThat(updated).isTrue();
        assertThat(updatedProject.dependencies)
            .containsEntry("com.strategicgains:RestExpress", "0.12.0-SNAPSHOT");
    }

    @Test
    void savesVersionFromExtendedArtifactCoordinates() throws Exception {
        Path projectFile = tempDir.resolve("project.json");
        ProjectJson project = new ProjectJson();
        project.save(projectFile);

        InstallCommand command = new InstallCommand();
        command.projectFile = projectFile;

        command.saveArtifactsToProject(List.of("com.example:tool:jar:sources:1.2.3"));

        ProjectJson updatedProject = ProjectJson.load(projectFile);
        assertThat(updatedProject.dependencies)
            .containsEntry("com.example:tool", "1.2.3");
    }
}
