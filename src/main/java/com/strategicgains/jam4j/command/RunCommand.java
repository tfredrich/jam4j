package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.model.ProjectJson;
import com.strategicgains.jam4j.resolver.ArtifactResolver;
import com.strategicgains.jam4j.script.ScriptRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Unmatched;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(
    name = "run",
    aliases = {"r"},
    description = "Execute an action script defined in project.json.",
    mixinStandardHelpOptions = false
)
public class RunCommand implements Runnable {

    @Mixin
    public CommonOptions opts;

    @Option(names = {"-l", "--list"}, description = "List all available scripts defined in project.json")
    public boolean list;

    @Option(names = {"-p", "--project"}, description = "Project file to use (default: ./project.json)")
    public Path projectFile = Path.of("project.json");

    @Unmatched
    public List<String> rawArgs = new ArrayList<>();

    @Override
    public void run() {
        try {
            if (!Files.exists(projectFile)) {
                System.err.println("Error: " + projectFile + " not found.");
                System.exit(1);
                return;
            }

            ProjectJson project = ProjectJson.load(projectFile);

            if (list) {
                if (project.scripts.isEmpty()) {
                    System.out.println("No scripts defined in " + projectFile);
                } else {
                    System.out.println("Available scripts:");
                    project.scripts.forEach((name, cmd) ->
                        System.out.printf("  %-20s %s%n", name, cmd));
                }
                return;
            }

            String prodClasspath = resolveProdClasspath(project);
            String devClasspath = resolveDevClasspath(project);

            // Parse rawArgs: "scriptName [-a arg]... scriptName [-a arg]..."
            List<ScriptExecution> executions = parseScriptExecutions(rawArgs, project.scripts);

            if (executions.isEmpty()) {
                System.err.println("No script specified. Use --list to see available scripts.");
                System.exit(1);
                return;
            }

            ScriptRunner runner = new ScriptRunner();
            Path workDir = projectFile.toAbsolutePath().getParent();

            for (ScriptExecution exec : executions) {
                if (!opts.quiet) System.out.println("> " + exec.name());
                int exitCode = runner.run(exec.script(), exec.args(), workDir, prodClasspath, devClasspath, opts.verbose);
                if (exitCode != 0) {
                    System.err.println("Script '" + exec.name() + "' failed with exit code " + exitCode);
                    System.exit(exitCode);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (opts.verbose) e.printStackTrace();
            System.exit(1);
        }
    }

    /** Resolve production-only deps to a classpath string (for {{deps}}). */
    String resolveProdClasspath(ProjectJson project) throws Exception {
        List<String> coords = project.dependencyCoords();
        if (coords.isEmpty()) return "";
        ArtifactResolver resolver = new ArtifactResolver(opts.resolveCache(), opts.ignorePomRepos, opts.verbose);
        List<File> jars = resolver.resolve(coords, opts.extraRepos);
        return jars.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
    }

    /** Resolve all deps (prod + dev) to a classpath string (for {{deps:dev}}). */
    String resolveDevClasspath(ProjectJson project) throws Exception {
        List<String> coords = project.allDependencyCoords();
        if (coords.isEmpty()) return "";
        ArtifactResolver resolver = new ArtifactResolver(opts.resolveCache(), opts.ignorePomRepos, opts.verbose);
        List<File> jars = resolver.resolve(coords, opts.extraRepos);
        return jars.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
    }

    /** Run a named script with optional extra args — used by convenience commands. */
    public void runScript(String scriptName, List<String> extraArgs) {
        rawArgs = new ArrayList<>();
        rawArgs.add(scriptName);
        if (extraArgs != null) {
            for (String arg : extraArgs) {
                rawArgs.add("-a");
                rawArgs.add(arg);
            }
        }
        run();
    }

    /**
     * Parse mixed positional args into (scriptName, args) pairs.
     * Format: scriptName [-a arg]... [scriptName [-a arg]...]
     */
    static List<ScriptExecution> parseScriptExecutions(List<String> rawArgs, Map<String, String> scripts) {
        List<ScriptExecution> executions = new ArrayList<>();
        String currentName = null;
        String currentScript = null;
        List<String> currentArgs = new ArrayList<>();

        for (int i = 0; i < rawArgs.size(); i++) {
            String arg = rawArgs.get(i);

            if ((arg.equals("-a") || arg.equals("--arg")) && i + 1 < rawArgs.size()) {
                currentArgs.add(rawArgs.get(++i));
            } else if (scripts.containsKey(arg)) {
                if (currentName != null) {
                    executions.add(new ScriptExecution(currentName, currentScript, new ArrayList<>(currentArgs)));
                }
                currentName = arg;
                currentScript = scripts.get(arg);
                currentArgs = new ArrayList<>();
            } else {
                if (currentName != null) {
                    currentArgs.add(arg);
                } else {
                    System.err.println("Unknown script: '" + arg + "'. Use --list to see available scripts.");
                    System.exit(1);
                }
            }
        }

        if (currentName != null) {
            executions.add(new ScriptExecution(currentName, currentScript, currentArgs));
        }
        return executions;
    }

    record ScriptExecution(String name, String script, List<String> args) {}
}
