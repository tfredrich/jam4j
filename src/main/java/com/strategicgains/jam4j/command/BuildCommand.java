package com.strategicgains.jam4j.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;

@Command(name = "build", description = "Execute the 'build' script defined in project.json.")
public class BuildCommand implements Runnable {

    @Mixin
    public CommonOptions opts;

    @Option(names = {"-p", "--project"}, description = "Project file to use (default: ./project.json)")
    public Path projectFile = Path.of("project.json");

    @Parameters(description = "Arguments to pass to the build script")
    public List<String> args;

    @Override
    public void run() {
        RunCommand run = new RunCommand();
        run.opts = opts;
        run.projectFile = projectFile;
        run.runScript("build", args);
    }
}
