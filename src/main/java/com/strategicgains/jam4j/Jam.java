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

    public static void main(String[] args) {
        System.exit(new CommandLine(new Jam()).execute(args));
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
