package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.install.Installer;
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

    @Parameters(description = "Artifacts to install (format: group:artifact:version). If none, installs from project.json.")
    public List<String> artifacts;

    @Override
    public void run() {
        try {
            ArtifactResolver resolver = new ArtifactResolver(opts.resolveCache());
            Installer installer = new Installer();
            List<String> coords;

            if (artifacts != null && !artifacts.isEmpty()) {
                coords = artifacts;
                if (!opts.quiet) System.out.println("Resolving " + coords.size() + " artifact(s)...");
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
            }

            List<File> resolved = resolver.resolve(coords, opts.extraRepos);
            if (!opts.quiet) System.out.println("Resolved " + resolved.size() + " JAR(s). Installing to " + opts.libDir + "...");
            installer.install(resolved, opts.libDir, opts.localOnly, opts.quiet);

            if (artifacts != null && !artifacts.isEmpty()) {
                boolean updated = saveArtifactsToProject(artifacts);
                if (updated && !opts.quiet) System.out.println("Updated " + projectFile);
            }

            if (!opts.quiet) System.out.println("Done.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (opts.verbose) e.printStackTrace();
            System.exit(1);
        }
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
