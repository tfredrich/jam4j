package com.strategicgains.jam4j.command;

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
import java.util.stream.Collectors;

@Command(
    name = "path",
    aliases = {"p"},
    description = "Print the classpath for the specified artifacts or project.json dependencies."
)
public class PathCommand implements Runnable {

    @Mixin
    public CommonOptions opts;

    @Option(names = {"-p", "--project"}, description = "Project file to use (default: ./project.json)")
    public Path projectFile = Path.of("project.json");

    @Parameters(description = "Artifacts to resolve (format: group:artifact:version). If none, uses project.json.")
    public List<String> artifacts;

    @Override
    public void run() {
        try {
            ArtifactResolver resolver = new ArtifactResolver(opts.resolveCache(), opts.ignorePomRepos, opts.verbose);
            List<String> coords;

            if (artifacts != null && !artifacts.isEmpty()) {
                coords = artifacts;
            } else {
                if (!Files.exists(projectFile)) {
                    System.err.println("Error: " + projectFile + " not found.");
                    System.exit(1);
                    return;
                }
                ProjectJson project = ProjectJson.load(projectFile);
                coords = project.allDependencyCoords();
                if (coords.isEmpty()) {
                    return; // empty classpath, print nothing
                }
            }

            List<File> resolved = resolver.resolve(coords, opts.extraRepos);
            String classpath = resolved.stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator));
            System.out.print(classpath);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (opts.verbose) e.printStackTrace();
            System.exit(1);
        }
    }
}
