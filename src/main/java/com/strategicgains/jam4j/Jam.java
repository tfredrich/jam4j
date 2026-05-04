package com.strategicgains.jam4j;

import com.strategicgains.jam4j.command.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "jam",
    description = "Java Artifact Manager — npm-like dependency management for Java",
    mixinStandardHelpOptions = true,
    version = "jam 1.0.0-SNAPSHOT",
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
}
