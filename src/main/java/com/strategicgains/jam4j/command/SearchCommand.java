package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.resolver.ArtifactResolver;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
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

    @Option(names = "--snapshots", description = "Include snapshot versions in search results")
    public boolean snapshots;

    @Option(names = "--local", description = "Include artifacts from the local Maven cache")
    public boolean local;

    @Option(names = "--no-central", description = "Skip Maven Central and search only local/cache or configured repositories")
    public boolean noCentral;

    @Option(names = {"-p", "--project"}, description = "Project file to use (default: ./project.json)")
    public Path projectFile = Path.of("project.json");

    @Parameters(description = "Partial or full artifact name to search for.", arity = "1..*")
    public List<String> queryParts;

    @Override
    public void run() {
        try {
            String query = String.join(" ", queryParts);
            ArtifactResolver resolver = new ArtifactResolver(opts.resolveCache());

            if (!opts.quiet) {
                System.out.println("Searching for '" + query + "'...\n");
                System.out.flush();
            }
            List<ArtifactResolver.SearchResult> results = resolver.search(query,
                new ArtifactResolver.SearchOptions(max, snapshots, local, !noCentral, opts.extraRepos));

            if (results.isEmpty()) {
                System.out.println("No results found for: " + query);
                return;
            }

            for (int i = 0; i < results.size(); i++) {
                ArtifactResolver.SearchResult r = results.get(i);
                System.out.printf("  [%2d] %-60s %-24s %s%n",
                    i + 1,
                    r.groupId() + ":" + r.artifactId(),
                    r.version(),
                    r.repositoryId());
            }

            if (interactive) {
                System.out.println("\nEnter number(s) to install (e.g. 1 3), or press Enter to skip:");
                Scanner scanner = new Scanner(System.in);
                if (!scanner.hasNextLine()) return;
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
                    install.artifacts = toInstall;
                    install.run();
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + describeError(e));
            if (opts.verbose) e.printStackTrace();
            System.exit(1);
        }
    }

    private String describeError(Exception e) {
        if (e instanceof HttpConnectTimeoutException || e instanceof HttpTimeoutException) {
            return "Timed out while searching remote repositories";
        }
        if (e instanceof ConnectException) {
            return "Could not connect while searching remote repositories";
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
