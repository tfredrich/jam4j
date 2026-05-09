package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.install.FatJarBuilder;
import com.strategicgains.jam4j.model.ProjectJson;
import com.strategicgains.jam4j.resolver.ArtifactResolver;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Command(name = "package", description = "Build a fat JAR, or run the 'package' script if one is defined in project.json.")
public class PackageCommand implements Runnable {

    @Mixin
    public CommonOptions opts;

    @Option(names = {"-p", "--project"}, description = "Project file to use (default: ./project.json)")
    public Path projectFile = Path.of("project.json");

    @Option(names = {"-o", "--output"}, description = "Output JAR path (default: target/<name>-<version>.jar)")
    public Path outputPath;

    @Option(names = {"--classes"}, description = "Compiled classes directory (default: target/classes)")
    public Path classesPath;

    @Parameters(description = "Arguments to pass to the package script (when a script is defined)")
    public List<String> args;

    @Override
    public void run() {
        try {
            Path workDir = projectFile.toAbsolutePath().getParent();

            if (!Files.exists(projectFile)) {
                System.err.println("Error: " + projectFile + " not found.");
                System.exit(1);
                return;
            }

            ProjectJson project = ProjectJson.load(projectFile);

            if (project.scripts.containsKey("package")) {
                RunCommand run = new RunCommand();
                run.opts = opts;
                run.projectFile = projectFile;
                run.runScript("package", args);
                return;
            }

            // Native fat JAR build
            Path outputJar = outputPath != null ? outputPath : defaultOutputJar(project, workDir);
            Path classesDir = classesPath != null ? classesPath : workDir.resolve("target/classes");

            if (!opts.quiet) System.out.println("Building fat JAR: " + outputJar);

            ArtifactResolver resolver = new ArtifactResolver(opts.resolveCache(), opts.ignorePomRepos, opts.verbose);
            List<File> depJars = resolver.resolve(project.dependencyCoords(), opts.extraRepos);

            new FatJarBuilder().build(depJars, classesDir, outputJar, project.mainClass, opts.quiet);

            if (!opts.quiet) System.out.println("Done.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (opts.verbose) e.printStackTrace();
            System.exit(1);
        }
    }

    private Path defaultOutputJar(ProjectJson project, Path workDir) {
        String name = project.name != null ? project.name : "app";
        String filename = project.version != null ? name + "-" + project.version + ".jar" : name + ".jar";
        return workDir.resolve("target").resolve(filename);
    }
}
