package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.model.ProjectJson;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunCommandTest {

    private RunCommand cmd() {
        RunCommand cmd = new RunCommand();
        cmd.opts = new CommonOptions();
        return cmd;
    }

    @Test
    void effectiveScriptsReturnsProjectScriptsUnchangedWhenRunIsDefined() {
        ProjectJson project = new ProjectJson();
        project.mainClass = "com.example.Main";
        project.scripts.put("run", "java -jar app.jar");

        Map<String, String> scripts = cmd().effectiveScripts(project);
        assertThat(scripts.get("run")).isEqualTo("java -jar app.jar");
    }

    @Test
    void effectiveScriptsInjectsDefaultRunScriptWhenMainClassSetAndNoRunScript() {
        ProjectJson project = new ProjectJson();
        project.mainClass = "com.example.Main";

        Map<String, String> scripts = cmd().effectiveScripts(project);
        assertThat(scripts).containsKey("run");
        assertThat(scripts.get("run")).contains("{{deps}}").contains("{{main}}");
    }

    @Test
    void effectiveScriptsDoesNotInjectRunScriptWhenMainClassIsNull() {
        ProjectJson project = new ProjectJson();

        Map<String, String> scripts = cmd().effectiveScripts(project);
        assertThat(scripts).doesNotContainKey("run");
    }

    @Test
    void effectiveScriptsInjectsDefaultPackageScriptWhenMainClassSetAndNoPackageScript() {
        ProjectJson project = new ProjectJson();
        project.mainClass = "com.example.Main";

        Map<String, String> scripts = cmd().effectiveScripts(project);
        assertThat(scripts).containsKey("package");
        assertThat(scripts.get("package")).contains("{{jarflags}}");
    }

    @Test
    void effectiveScriptsDoesNotInjectPackageScriptWhenMainClassIsNull() {
        ProjectJson project = new ProjectJson();

        Map<String, String> scripts = cmd().effectiveScripts(project);
        assertThat(scripts).doesNotContainKey("package");
    }

    @Test
    void effectiveScriptsDoesNotOverrideExistingPackageScript() {
        ProjectJson project = new ProjectJson();
        project.mainClass = "com.example.Main";
        project.scripts.put("package", "mvn package");

        Map<String, String> scripts = cmd().effectiveScripts(project);
        assertThat(scripts.get("package")).isEqualTo("mvn package");
    }
}
