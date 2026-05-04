package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.resolver.ArtifactResolver;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@Command(
    name = "search",
    aliases = {"s"},
    description = "Search for Maven artifacts in repositories."
)
public class SearchCommand implements Runnable {

    @Mixin
    public CommonOptions opts;

    @Option(names = {"-i", "--interactive"}, description = "Interactively search and select artifacts to install")
    public boolean interactive;

    @Option(names = {"-m", "--max"}, description = "Maximum number of results to return", defaultValue = "20")
    public int max = 20;

    @Option(names = {"-p", "--project"}, description = "Project file to use (default: ./project.json)")
    public Path projectFile = Path.of("project.json");

    @Parameters(description = "Partial or full artifact name to search for.", arity = "1..*")
    public List<String> queryParts;

    @Override
    public void run() {
        try {
            String query = String.join(" ", queryParts);
            ArtifactResolver resolver = new ArtifactResolver(opts.resolveCache());

            if (!opts.quiet) System.out.println("Searching for '" + query + "'...\n");
            List<ArtifactResolver.SearchResult> results = resolver.search(query, max);

            if (results.isEmpty()) {
                System.out.println("No results found for: " + query);
                return;
            }

            for (int i = 0; i < results.size(); i++) {
                ArtifactResolver.SearchResult r = results.get(i);
                System.out.printf("  [%2d] %-60s %s%n",
                    i + 1,
                    r.groupId() + ":" + r.artifactId(),
                    r.latestVersion());
            }

            if (interactive) {
                System.out.println("\nEnter number(s) to install (e.g. 1 3), or press Enter to skip:");
                Scanner scanner = new Scanner(System.in);
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) return;

                List<String> toInstall = new ArrayList<>();
                for (String token : line.split("\\s+")) {
                    try {
                        int idx = Integer.parseInt(token) - 1;
                        if (idx >= 0 && idx < results.size()) {
                            toInstall.add(results.get(idx).coord());
                        } else {
                            System.err.println("Invalid selection: " + token);
                        }
                    } catch (NumberFormatException ignored) {
                        System.err.println("Invalid selection: " + token);
                    }
                }

                if (!toInstall.isEmpty()) {
                    InstallCommand install = new InstallCommand();
                    install.opts = opts;
                    install.projectFile = projectFile;
                    install.save = true;
                    install.artifacts = toInstall;
                    install.run();
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (opts.verbose) e.printStackTrace();
            System.exit(1);
        }
    }
}
