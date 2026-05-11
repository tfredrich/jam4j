package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.model.PomReader;
import com.strategicgains.jam4j.model.ProjectJson;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(
    name = "init",
    aliases = {"n"},
    description = "Create a scaffold project.json."
)
public class InitCommand implements Callable<Integer> {

    private static final String DEFAULT_TEST_SCRIPT =
        "javac -cp {{deps:dev}} -d {{classes}} {{sources}}" +
        " && javac -cp {{deps:dev}}{:}{{classes}} -d {{classes:test}} {{sources:test}}" +
        " && java -cp {{deps:dev}}{:}{{classes}}{:}{{classes:test}}" +
        " org.junit.platform.console.ConsoleLauncher --scan-classpath --disable-banner";

    @Option(names = {"-f", "--from"}, description = "Bootstrap project.json from a Maven pom.xml file")
    public File pomFile;

    @Option(names = "--force", description = "Overwrite project.json if it already exists")
    public boolean force;

    @Option(names = "--name", description = "Project name")
    public String projectName;

    @Option(names = "--version", description = "Project version")
    public String version;

    @Option(names = "--main", description = "Main class")
    public String mainClass;

    @Parameters(index = "0", arity = "0..1", description = "Target directory (default: current directory)")
    public Path targetDirectory = Path.of(".");

    @Override
    public Integer call() {
        try {
            Path projectRoot = targetDirectory.toAbsolutePath().normalize();
            Path projectFile = projectRoot.resolve("project.json");

            if (pomFile != null) {
                if (!pomFile.exists()) {
                    System.err.println("Error: " + pomFile + " not found.");
                    return 1;
                }
                if (Files.exists(projectFile) && !force) {
                    System.err.println("Error: " + projectFile + " already exists. Use --force to overwrite it.");
                    return 1;
                }
                PomReader pom = PomReader.from(pomFile);
                ProjectJson project = new ProjectJson();
                project.name = projectName != null ? projectName : pom.getName();
                project.version = version != null ? version : pom.getVersion();
                String mc = mainClass != null ? mainClass : pom.getMainClass();
                project.mainClass = mc;
                for (Map.Entry<String, String> e : pom.getDependencies().entrySet())
                    project.addDependency(e.getKey(), e.getValue());
                for (Map.Entry<String, String> e : pom.getDevDependencies().entrySet())
                    project.addDevDependency(e.getKey(), e.getValue());
                Files.createDirectories(projectRoot);
                saveWithCommentedScripts(project, projectFile, testScript(pom));
                System.out.println("Created " + projectFile);
                return 0;
            }

            if (Files.exists(projectFile) && !force) {
                System.err.println("Error: " + projectFile + " already exists. Use --force to overwrite it.");
                return 1;
            }

            Files.createDirectories(projectRoot);
            Scanner scanner = new Scanner(System.in);

            ProjectJson project = new ProjectJson();
            project.name = prompt("name", defaultProjectName(projectRoot), projectName, scanner);
            project.version = prompt("version", "0.1.0", version, scanner);
            project.mainClass = blankToNull(prompt("main class", "", mainClass, scanner));

            saveWithCommentedScripts(project, projectFile, DEFAULT_TEST_SCRIPT);
            System.out.println("Created " + projectFile);
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private String defaultProjectName(Path projectRoot) {
        Path fileName = projectRoot.getFileName();
        return fileName == null ? "jam-project" : fileName.toString();
    }

    private String prompt(String label, String defaultValue, String configuredValue, Scanner scanner) {
        if (configuredValue != null) return configuredValue;

        String answer;
        Console console = System.console();
        if (console != null) {
            answer = console.readLine("%s%s: ", label, defaultValue.isBlank() ? "" : " (" + defaultValue + ")");
        } else {
            System.out.printf("%s%s: ", label, defaultValue.isBlank() ? "" : " (" + defaultValue + ")");
            answer = scanner.nextLine();
        }

        if (answer == null || answer.isBlank()) return defaultValue;
        return answer.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String testScript(PomReader pom) {
        boolean junit4 = pom.getDependencies().containsKey("junit:junit")
                      || pom.getDevDependencies().containsKey("junit:junit");
        if (junit4) {
            return "javac -cp {{deps:dev}} -d {./target/classes} {{sources}}" +
                   " && javac -cp {{deps:dev}}{:}{./target/classes} -d {./target/test-classes} {{tests}}" +
                   " && java -cp {{deps:dev}}{:}{{classes}}{:}{{classes:test}}" +
                   " org.junit.runner.JUnitCore {{classNames:test}}";
        }
        return DEFAULT_TEST_SCRIPT;
    }

    private void saveWithCommentedScripts(ProjectJson project, Path projectFile, String testScript) throws IOException {
        String json = project.toJsonString();
        String commentedScripts =
            "\"scripts\" : {\n" +
            "    \"test\" : \"" + testScript + "\",\n" +
            "    // \"build\" : \"javac -cp {{deps}} -d {{classes}} {{sources}}\",\n" +
            "    // \"run\" : \"java -cp {{deps}}{:}{{classes}} {{main}}\",\n" +
            "    // \"clean\" : \"rm -rf {{output}}\",\n" +
            "    // \"package\" : \"<your package command>\"\n" +
            "  }";
        json = json.replace("\"scripts\" : { }", commentedScripts);
        Files.writeString(projectFile, json);
    }
}
