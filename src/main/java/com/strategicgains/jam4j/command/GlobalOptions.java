package com.strategicgains.jam4j.command;

import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Options that establish the context for the complete jam invocation. */
public class GlobalOptions {

    @Option(names = {"-p", "--project"}, description = "Project file to use (default: ./project.json)")
    public Path projectFile = Path.of("project.json");

    @Option(names = "--config", description = "Path to user configuration file")
    public Path configFile;

    @Option(names = {"-c", "--cache"}, description = "Directory where downloaded artifacts are cached")
    public Path cacheDir;

    @Option(names = {"-r", "--repo"}, description = "URL to an additional repository (repeatable)")
    public List<String> extraRepos = new ArrayList<>();

    @Option(names = "--ignore-pom-repos", description = "Ignore repositories declared in dependency POMs")
    public boolean ignorePomRepos;

    @Option(names = {"-q", "--quiet"}, description = "Don't output non-essential information")
    public boolean quiet;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output for debugging")
    public boolean verbose;

    public List<String> asArguments() {
        List<String> args = new ArrayList<>();
        args.add("--project");
        args.add(projectFile.toString());
        if (configFile != null) { args.add("--config"); args.add(configFile.toString()); }
        if (cacheDir != null) { args.add("--cache"); args.add(cacheDir.toString()); }
        for (String repo : extraRepos) { args.add("--repo"); args.add(repo); }
        if (ignorePomRepos) args.add("--ignore-pom-repos");
        if (quiet) args.add("--quiet");
        if (verbose) args.add("--verbose");
        return args;
    }
}
