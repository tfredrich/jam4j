package com.strategicgains.jam4j.script;

import java.nio.file.Path;

public record ScriptVariables(
    String prodClasspath,
    String devClasspath,
    Path sourceDir,
    Path testDir,
    Path classesDir,
    Path testClassesDir,
    String mainClass
) {}
