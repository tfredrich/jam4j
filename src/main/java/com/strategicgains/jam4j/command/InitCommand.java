package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.model.PomReader;
import com.strategicgains.jam4j.model.ProjectJson;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.Console;
import java.io.File;
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
                project.scripts.put("build", "javac -cp {{deps}} -d {./target/classes} $(find {./src/main/java} -name \"*.java\")");
                project.scripts.put("test", "javac -cp {./target/classes}{:}{{deps}} -d {./target/test-classes} $(find {./src/test/java} -name \"*.java\")");
                project.scripts.put("run", "java -cp {./target/classes}{:}{{deps}} " + (mc != null ? mc : "<mainClass>"));
                project.scripts.put("clean", "rm -rf {./target/classes} {./target/test-classes}");
                for (Map.Entry<String, String> e : pom.getDependencies().entrySet())
                    project.addDependency(e.getKey(), e.getValue());
                for (Map.Entry<String, String> e : pom.getDevDependencies().entrySet())
                    project.addDevDependency(e.getKey(), e.getValue());
                Files.createDirectories(projectRoot);
                project.save(projectFile);
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
            project.scripts.put("build", "javac -cp {{deps}} -d {./target/classes} {./src/main/java/Main.java}");
            project.scripts.put("test", "java -ea -cp {./target/classes}{:}{{deps}} MainTest");
            project.scripts.put("run", "java -cp {./target/classes}{:}{{deps}} " + (project.mainClass == null ? "Main" : project.mainClass));
            project.scripts.put("clean", "rm -rf {./target}");

            project.save(projectFile);
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
}
