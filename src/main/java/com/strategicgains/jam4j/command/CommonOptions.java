package com.strategicgains.jam4j.command;

import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CommonOptions {

    @Option(names = "--config", hidden = true,
        description = "Path to user configuration file (default: JAM_CONFIG env var, ~/.jam/config.json, or ~/.jamcfg.json)")
    public Path configFile;

    @Option(names = {"-c", "--cache"}, hidden = true,
        description = "Directory where downloaded artifacts are cached (default: JAM_CACHE env var or ~/.m2/repository)")
    public Path cacheDir;

    public Path libDir = Path.of("lib");

    public boolean localOnly;

    @Option(names = {"-r", "--repo"}, hidden = true,
        description = "URL to additional repository (e.g. -r myrepo=https://my.repo.com/maven2). Set JAM_REPO_<name>_USER and JAM_REPO_<name>_PASSWORD for auth.")
    public List<String> extraRepos = new ArrayList<>();

    @Option(names = "--ignore-pom-repos", hidden = true,
        description = "Ignore repositories declared in dependency POMs; resolve only from Maven Central and --repo repositories.")
    public boolean ignorePomRepos;

    @Option(names = {"-q", "--quiet"}, hidden = true, description = "Don't output non-essential information")
    public boolean quiet;

    @Option(names = {"-v", "--verbose"}, hidden = true, description = "Enable verbose output for debugging")
    public boolean verbose;

    /** Returns the effective local repository cache path, checking env and defaults. */
    public Path resolveCache() {
        if (cacheDir != null) return cacheDir;
        String envCache = System.getenv("JAM_CACHE");
        if (envCache != null) return Path.of(envCache);
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }
}
