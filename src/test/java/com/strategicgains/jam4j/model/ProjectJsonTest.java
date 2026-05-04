package com.strategicgains.jam4j.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectJsonTest {

    @TempDir
    Path tempDir;

    @Test
    void loadFromFile() throws IOException {
        String json = """
            {
              "name": "test-app",
              "version": "1.0.0",
              "main": "com.example.Main",
              "dependencies": {
                "com.example:foo": "1.0.0"
              },
              "devDependencies": {
                "org.junit.jupiter:junit-jupiter": "5.10.0"
              },
              "scripts": {
                "build": "javac -cp {{deps}} Main.java"
              }
            }
            """;
        Path file = tempDir.resolve("project.json");
        Files.writeString(file, json);

        ProjectJson project = ProjectJson.load(file);

        assertThat(project.name).isEqualTo("test-app");
        assertThat(project.version).isEqualTo("1.0.0");
        assertThat(project.mainClass).isEqualTo("com.example.Main");
        assertThat(project.dependencies).containsKey("com.example:foo");
        assertThat(project.devDependencies).containsKey("org.junit.jupiter:junit-jupiter");
        assertThat(project.scripts).containsKey("build");
    }

    @Test
    void saveAndReloadRoundTrip() throws IOException {
        ProjectJson project = new ProjectJson();
        project.name = "my-app";
        project.version = "2.0.0";
        project.addDependency("com.example:bar", "3.0.0");
        project.scripts.put("build", "javac Main.java");

        Path file = tempDir.resolve("project.json");
        project.save(file);

        ProjectJson loaded = ProjectJson.load(file);

        assertThat(loaded.name).isEqualTo("my-app");
        assertThat(loaded.version).isEqualTo("2.0.0");
        assertThat(loaded.dependencies).containsEntry("com.example:bar", "3.0.0");
        assertThat(loaded.scripts).containsEntry("build", "javac Main.java");
    }

    @Test
    void dependencyCoordsConversion() throws IOException {
        String json = """
            {
              "dependencies": {
                "com.example:foo": "1.0",
                "com.example:bar": "2.0"
              },
              "devDependencies": {
                "org.junit:junit": "5.0"
              }
            }
            """;
        Path file = tempDir.resolve("project.json");
        Files.writeString(file, json);
        ProjectJson project = ProjectJson.load(file);

        List<String> depCoords = project.dependencyCoords();
        assertThat(depCoords).containsExactlyInAnyOrder("com.example:foo:1.0", "com.example:bar:2.0");

        List<String> devCoords = project.devDependencyCoords();
        assertThat(devCoords).containsExactly("org.junit:junit:5.0");

        List<String> allCoords = project.allDependencyCoords();
        assertThat(allCoords).hasSize(3);
    }

    @Test
    void ignoredUnknownFields() throws IOException {
        String json = """
            {
              "name": "test",
              "unknownField": "should be ignored",
              "anotherUnknown": 42
            }
            """;
        Path file = tempDir.resolve("project.json");
        Files.writeString(file, json);

        ProjectJson project = ProjectJson.load(file);
        assertThat(project.name).isEqualTo("test");
    }
}
