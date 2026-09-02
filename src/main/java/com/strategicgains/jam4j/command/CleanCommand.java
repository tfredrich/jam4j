package com.strategicgains.jam4j.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;

@Command(name = "clean", mixinStandardHelpOptions = true,
    description = "Execute the 'clean' script defined in project.json.")
public class CleanCommand implements Runnable {

    @Mixin
    public CommonOptions opts;

    @Option(names = {"-p", "--project"}, hidden = true, description = "Project file to use (default: ./project.json)")
    public Path projectFile = Path.of("project.json");

    @Parameters(description = "Arguments to pass to the clean script")
    public List<String> args;

    @Override
    public void run() {
        RunCommand run = new RunCommand();
        run.opts = opts;
        run.projectFile = projectFile;
        run.runScript("clean", args);
    }
}
