package com.strategicgains.jam4j.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strategicgains.jam4j.Jam;
import com.strategicgains.jam4j.model.ProjectJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InitCommandTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void createsProjectJsonInTargetDirectory() throws Exception {
        Path target = tempDir.resolve("sample-app");

        int exitCode = new CommandLine(new InitCommand()).execute(
            "--name", "sample-app",
            "--version", "1.2.3",
            "--main", "com.example.Main",
            target.toString());

        assertThat(exitCode).isZero();

        ProjectJson project = ProjectJson.load(target.resolve("project.json"));
        assertThat(project.name).isEqualTo("sample-app");
        assertThat(project.version).isEqualTo("1.2.3");
        assertThat(project.mainClass).isEqualTo("com.example.Main");
        assertThat(project.dependencies).isEmpty();
        assertThat(project.scripts)
            .containsKeys("build", "test", "run", "clean")
            .containsEntry("run", "java -cp {./target/classes}{:}{{deps}} com.example.Main");
    }

    @Test
    void promptsForMissingValuesUsingDefaults() throws Exception {
        Path target = tempDir.resolve("prompted-app");
        InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream("\n\n\n".getBytes(StandardCharsets.UTF_8)));
        try {
            int exitCode = new CommandLine(new InitCommand()).execute(target.toString());

            assertThat(exitCode).isZero();
        } finally {
            System.setIn(originalIn);
        }

        JsonNode project = MAPPER.readTree(target.resolve("project.json").toFile());
        assertThat(project.get("name").asText()).isEqualTo("prompted-app");
        assertThat(project.get("version").asText()).isEqualTo("0.1.0");
        assertThat(project.has("main")).isFalse();
    }

    @Test
    void refusesToOverwriteExistingProjectJsonWithoutForce() throws Exception {
        Path projectFile = tempDir.resolve("project.json");
        Files.writeString(projectFile, "{\"name\":\"existing\"}");

        int exitCode = new CommandLine(new InitCommand()).execute(
            "--name", "new-name",
            tempDir.toString());

        assertThat(exitCode).isEqualTo(1);
        assertThat(Files.readString(projectFile)).isEqualTo("{\"name\":\"existing\"}");
    }

    @Test
    void overwritesExistingProjectJsonWithForce() throws Exception {
        Path projectFile = tempDir.resolve("project.json");
        Files.writeString(projectFile, "{\"name\":\"existing\"}");

        int exitCode = new CommandLine(new InitCommand()).execute(
            "--force",
            "--name", "new-name",
            "--version", "2.0.0",
            "--main", "",
            tempDir.toString());

        assertThat(exitCode).isZero();

        ProjectJson project = ProjectJson.load(projectFile);
        assertThat(project.name).isEqualTo("new-name");
        assertThat(project.version).isEqualTo("2.0.0");
    }

    @Test
    void rootCommandRegistersInitAlias() throws Exception {
        Path target = tempDir.resolve("alias-app");

        int exitCode = new CommandLine(new Jam()).execute(
            "n",
            "--name", "alias-app",
            "--version", "0.2.0",
            "--main", "",
            target.toString());

        assertThat(exitCode).isZero();
        assertThat(target.resolve("project.json")).exists();
    }
}
