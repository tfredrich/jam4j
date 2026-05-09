package com.strategicgains.jam4j.script;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ScriptRunner {

    private final VariableSubstitutor substitutor = new VariableSubstitutor();

    /**
     * Substitute variables in the script string, append any extra args, tokenize, and execute.
     * The process inherits stdin/stdout/stderr from the jam process.
     */
    public int run(String script, List<String> extraArgs, Path workDir, ScriptVariables vars, boolean verbose)
            throws IOException, InterruptedException {

        String resolved = substitutor.substitute(script, vars, workDir);
        if (extraArgs != null && !extraArgs.isEmpty()) {
            resolved = resolved + " " + String.join(" ", extraArgs);
        }

        if (verbose) System.out.println("$ " + resolved);

        List<String> shellCmd = isWindows()
            ? List.of("cmd", "/c", resolved)
            : List.of("sh", "-c", resolved);

        return new ProcessBuilder(shellCmd)
            .directory(workDir.toFile())
            .inheritIO()
            .start()
            .waitFor();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    /**
     * Split a command string into tokens respecting single and double quoted strings.
     * Quotes are stripped from the resulting tokens.
     */
    static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == ' ' && !inSingle && !inDouble) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }
}
