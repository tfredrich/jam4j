package com.strategicgains.jam4j;

import com.strategicgains.jam4j.command.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

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
        PackageCommand.class
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
            Package jamPackage = Jam.class.getPackage();
            String version = firstNonBlank(metadata.version(), jamPackage.getImplementationVersion());

            return new String[] { formatVersion(version, metadata.buildTime()) };
        }
    }

    static String formatVersion(String version, String buildTime) {
        String resolvedVersion = isBlank(version) ? "dev" : version;

        if (isBlank(buildTime)) {
            return "jam " + resolvedVersion;
        }

        return "jam " + resolvedVersion + " (built " + buildTime + ")";
    }

    private static BuildMetadata readBuildMetadata() {
        Properties properties = new Properties();

        try (InputStream stream = Jam.class.getResourceAsStream("/com/strategicgains/jam4j/build.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException e) {
            return BuildMetadata.empty();
        }

        return new BuildMetadata(
            properties.getProperty("version"),
            properties.getProperty("build.time"));
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record BuildMetadata(String version, String buildTime) {

        private static BuildMetadata empty() {
            return new BuildMetadata(null, null);
        }
    }
}
