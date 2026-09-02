package com.strategicgains.jam4j;

import com.strategicgains.jam4j.command.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Optional;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Command(
    name = "jam",
    description = "Java Artifact Manager — npm-like dependency management for Java",
    mixinStandardHelpOptions = true,
    versionProvider = Jam.VersionProvider.class,
    subcommands = {
        SearchCommand.class,
        InitCommand.class,
        InstallCommand.class,
        PathCommand.class,
        RunCommand.class,
        BuildCommand.class,
        TestCommand.class,
        CleanCommand.class,
        PackageCommand.class,
        UpgradeCommand.class
    }
)
public class Jam implements Runnable {

    @picocli.CommandLine.Mixin
    public GlobalOptions globalOptions = new GlobalOptions();

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    /** Executes global options followed by one or more commands. */
    static int execute(String[] args) {
        try {
            Invocation invocation = Invocation.parse(args);
            if (invocation.commands().isEmpty()) {
                return new CommandLine(new Jam()).execute(args);
            }
            if (invocation.commands().size() > 1 && invocation.commands().contains("upgrade")) {
                System.err.println("Error: 'upgrade' cannot be chained with other commands.");
                return 2;
            }

            int exitCode = 0;
            for (List<String> command : invocation.commandsAsArguments()) {
                List<String> commandArgs = command.subList(1, command.size());
                List<String> globalArgs = command.getFirst().equals("upgrade")
                    ? List.of()
                    : invocation.globalArguments();
                exitCode = new CommandLine(commandObject(command.get(0))).execute(
                    combine(globalArgs, commandArgs));
                if (exitCode != 0) return exitCode;
            }
            return exitCode;
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    private static Object commandObject(String name) {
        return switch (name) {
            case "search", "s" -> new SearchCommand();
            case "init", "n" -> new InitCommand();
            case "install", "i" -> new InstallCommand();
            case "path", "p" -> new PathCommand();
            case "run", "r" -> new RunCommand();
            case "build" -> new BuildCommand();
            case "test" -> new TestCommand();
            case "clean" -> new CleanCommand();
            case "package" -> new PackageCommand();
            case "upgrade" -> new UpgradeCommand();
            default -> throw new IllegalArgumentException("unknown command: " + name);
        };
    }

    private static String[] combine(List<String> global, List<String> command) {
        List<String> result = new java.util.ArrayList<>(global);
        result.addAll(command);
        return result.toArray(String[]::new);
    }

    record Invocation(List<String> globalArguments, List<List<String>> commandArguments) {
        static Invocation parse(String[] args) {
            List<String> globals = new java.util.ArrayList<>();
            List<List<String>> commandArgs = new java.util.ArrayList<>();
            List<String> current = null;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (current == null) {
                    if (isCommand(arg)) {
                        current = new java.util.ArrayList<>();
                        current.add(arg);
                        commandArgs.add(current);
                    } else {
                        if (isGlobalWithValue(arg)) {
                            if (++i >= args.length) throw new IllegalArgumentException("missing value for " + arg);
                            globals.add(arg); globals.add(args[i]);
                        } else if (isGlobal(arg)) {
                            globals.add(arg);
                        } else if (arg.equals("--help") || arg.equals("-h") || arg.equals("--version") || arg.equals("-V")) {
                            return new Invocation(List.of(), List.of());
                        } else {
                            throw new IllegalArgumentException("global option expected before command: " + arg);
                        }
                    }
                } else if (isCommand(arg)) {
                    current = new java.util.ArrayList<>();
                    current.add(arg);
                    commandArgs.add(current);
                } else {
                    if (isGlobal(arg) || isGlobalWithValue(arg))
                        throw new IllegalArgumentException("global option must appear before the command: " + arg);
                    current.add(arg);
                }
            }
            return new Invocation(globals, commandArgs);
        }

        List<List<String>> commandsAsArguments() {
            return commandArguments;
        }

        List<String> commands() {
            return commandArguments.stream().map(List::getFirst).toList();
        }

        private static boolean isCommand(String arg) {
            return switch (arg) {
                case "search", "s", "init", "n", "install", "i", "path", "p", "run", "r",
                     "build", "test", "clean", "package", "upgrade" -> true;
                default -> false;
            };
        }

        private static boolean isGlobal(String arg) {
            return List.of("--ignore-pom-repos", "-q", "--quiet", "-v", "--verbose").contains(arg);
        }

        private static boolean isGlobalWithValue(String arg) {
            return List.of("-p", "--project", "--config", "-c", "--cache", "-r", "--repo").contains(arg);
        }
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static final class VersionProvider implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() {
            BuildMetadata metadata = readBuildMetadata();
            return new String[] { formatVersion(metadata.version(), metadata.buildTime()) };
        }
    }

    static String formatVersion(String version, String buildTime) {
        String resolvedVersion = isBlank(version) ? "dev" : version;

        if (isBlank(buildTime)) {
            return "jam " + resolvedVersion;
        }

        return "jam " + resolvedVersion + " (build " + buildTime + ")";
    }

    private static BuildMetadata readBuildMetadata() {
        return new BuildMetadata(
            Jam.class.getPackage().getImplementationVersion(),
            readManifestBuildTime().orElse(null));
    }

    private static Optional<String> readManifestBuildTime() {
        try {
            CodeSource codeSourceLocation = Jam.class.getProtectionDomain().getCodeSource();
            if (codeSourceLocation == null) {
                return Optional.empty();
            }

            Path codeSource = Path.of(codeSourceLocation.getLocation().toURI());
            if (!Files.isRegularFile(codeSource)) {
                return Optional.empty();
            }

            try (JarFile jar = new JarFile(codeSource.toFile())) {
                Manifest manifest = jar.getManifest();
                if (manifest == null) {
                    return Optional.empty();
                }

                Attributes attributes = manifest.getMainAttributes();
                return Optional.ofNullable(attributes.getValue("Build-Time")).filter(value -> !value.isBlank());
            }
        } catch (IOException | IllegalArgumentException | SecurityException | URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record BuildMetadata(String version, String buildTime) {
    }
}
