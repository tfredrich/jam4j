package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.install.Installer;
import com.strategicgains.jam4j.model.PomReader;
import com.strategicgains.jam4j.model.ProjectJson;
import com.strategicgains.jam4j.resolver.ArtifactResolver;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Command(
    name = "install",
    aliases = {"i"},
    description = "Install artifacts and add them to project.json dependencies."
)
public class InstallCommand implements Runnable {

    @Mixin
    public CommonOptions opts;

    @Option(names = {"-p", "--project"}, description = "Project file to use (default: ./project.json)")
    public Path projectFile = Path.of("project.json");

    @Option(names = {"-f", "--from"}, description = "Read dependencies from a Maven pom.xml file")
    public File pomFile;

    @Parameters(description = "Artifacts to install (format: group:artifact:version). If none, installs from project.json.")
    public List<String> artifacts;

    @Override
    public void run() {
        try {
            ArtifactResolver resolver = new ArtifactResolver(opts.resolveCache(), opts.ignorePomRepos, opts.verbose);
            Installer installer = new Installer();
            List<String> coords;

            if (pomFile != null) {
                if (!pomFile.exists()) {
                    System.err.println("Error: " + pomFile + " not found.");
                    System.exit(1);
                    return;
                }
                PomReader pom = PomReader.from(pomFile);
                coords = pom.allCoords();
                if (coords.isEmpty()) {
                    System.out.println("No dependencies found in " + pomFile);
                    return;
                }
                if (!opts.quiet) System.out.println("Installing " + coords.size() + " dependencies from " + pomFile + "...");
                List<File> resolved = resolver.resolve(coords, opts.extraRepos);
                if (!opts.quiet) System.out.println("Resolved " + resolved.size() + " JAR(s). Installing to " + opts.libDir + "...");
                installer.install(resolved, opts.libDir, opts.localOnly, opts.quiet);
                savePomToProject(pom);
                if (!opts.quiet) System.out.println("Updated " + projectFile);
            } else if (artifacts != null && !artifacts.isEmpty()) {
                coords = artifacts;
                if (!opts.quiet) System.out.println("Resolving " + coords.size() + " artifact(s)...");
                List<File> resolved = resolver.resolve(coords, opts.extraRepos);
                if (!opts.quiet) System.out.println("Resolved " + resolved.size() + " JAR(s). Installing to " + opts.libDir + "...");
                installer.install(resolved, opts.libDir, opts.localOnly, opts.quiet);
                boolean updated = saveArtifactsToProject(artifacts);
                if (updated && !opts.quiet) System.out.println("Updated " + projectFile);
            } else {
                if (!Files.exists(projectFile)) {
                    System.err.println("Error: " + projectFile + " not found. Create a project.json or specify artifacts to install.");
                    System.exit(1);
                    return;
                }
                ProjectJson project = ProjectJson.load(projectFile);
                coords = project.allDependencyCoords();
                if (coords.isEmpty()) {
                    System.out.println("No dependencies found in " + projectFile);
                    return;
                }
                if (!opts.quiet) System.out.println("Installing " + coords.size() + " dependencies from " + projectFile + "...");
                List<File> resolved = resolver.resolve(coords, opts.extraRepos);
                if (!opts.quiet) System.out.println("Resolved " + resolved.size() + " JAR(s). Installing to " + opts.libDir + "...");
                installer.install(resolved, opts.libDir, opts.localOnly, opts.quiet);
            }

            if (!opts.quiet) System.out.println("Done.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (opts.verbose) e.printStackTrace();
            System.exit(1);
        }
    }

    void savePomToProject(PomReader pom) throws IOException {
        ProjectJson project;
        if (Files.exists(projectFile)) {
            project = ProjectJson.load(projectFile);
        } else {
            project = new ProjectJson();
            project.name = pom.getName();
            project.version = pom.getVersion();
            String mainClass = pom.getMainClass();
            project.mainClass = mainClass;
            project.scripts.put("build", "javac -cp {{deps}} -d {./target/classes} $(find {./src/main/java} -name \"*.java\")");
            project.scripts.put("test", "javac -cp {./target/classes}{:}{{deps}} -d {./target/test-classes} $(find {./src/test/java} -name \"*.java\")");
            project.scripts.put("run", "java -cp {./target/classes}{:}{{deps}} " + (mainClass != null ? mainClass : "<mainClass>"));
            project.scripts.put("clean", "rm -rf {./target/classes} {./target/test-classes}");
        }
        for (Map.Entry<String, String> e : pom.getDependencies().entrySet()) {
            project.addDependency(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : pom.getDevDependencies().entrySet()) {
            project.addDevDependency(e.getKey(), e.getValue());
        }
        project.save(projectFile);
    }

    boolean saveArtifactsToProject(List<String> artifacts) throws IOException {
        if (!Files.exists(projectFile)) {
            return false;
        }

        ProjectJson project = ProjectJson.load(projectFile);
        for (String coord : artifacts) {
            String[] parts = coord.split(":");
            if (parts.length >= 3) {
                String ga = parts[0] + ":" + parts[1];
                String ver = parts[parts.length - 1];
                project.addDependency(ga, ver);
            }
        }
        project.save(projectFile);
        return true;
    }
}
