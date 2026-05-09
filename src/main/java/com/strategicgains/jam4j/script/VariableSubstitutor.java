package com.strategicgains.jam4j.script;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Substitutes jam template variables in script strings before execution.
 *
 * Supported tokens:
 *   {{deps}}          — resolved classpath of production dependencies only
 *   {{deps:dev}}      — resolved classpath of production + dev dependencies
 *   {{sources}}       — space-separated list of *.java files under sourceDir
 *   {{tests}}         — space-separated list of *.java files under testDir
 *   {{main}}          — main class from project.json
 *   {{jarflags}}      — "--main-class <mainClass>" when main is set, else empty
 *   {/}               — platform file separator (\ on Windows, / on Mac/Linux)
 *   {:}               — platform path separator (; on Windows, : on Mac/Linux)
 *   {~}               — user home directory
 *   {./path/to/file}  — relative path normalized for the current platform
 *   {./a:./b:~/c}     — colon-separated paths, each normalized and rejoined with pathSeparator
 */
public class VariableSubstitutor {

    // Matches any {{…}} token first (greedy two-brace form), then any single-brace {…} token
    private static final Pattern TOKEN = Pattern.compile("\\{\\{[^}]+\\}\\}|\\{[^}]+\\}");

    public String substitute(String script, ScriptVariables vars, Path projectRoot) {
        Matcher m = TOKEN.matcher(script);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(
                resolveToken(m.group(), vars, projectRoot)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String resolveToken(String token, ScriptVariables vars, Path projectRoot) {
        return switch (token) {
            case "{{deps}}"     -> vars.prodClasspath();
            case "{{deps:dev}}" -> vars.devClasspath();
            case "{{sources}}"  -> collectJavaFiles(projectRoot.resolve(vars.sourceDir()));
            case "{{tests}}"    -> collectJavaFiles(projectRoot.resolve(vars.testDir()));
            case "{{main}}"     -> vars.mainClass() != null ? vars.mainClass() : "";
            case "{{jarflags}}" -> vars.mainClass() != null ? "--main-class " + vars.mainClass() : "";
            default             -> resolveSingleBraceToken(token, projectRoot);
        };
    }

    private String collectJavaFiles(Path dir) {
        if (!Files.isDirectory(dir)) return "";
        try (var stream = Files.walk(dir)) {
            return stream
                .filter(p -> p.toString().endsWith(".java"))
                .sorted()
                .map(Path::toString)
                .collect(Collectors.joining(" "));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String resolveSingleBraceToken(String token, Path projectRoot) {
        String content = token.substring(1, token.length() - 1); // strip { }

        return switch (content) {
            case "/"  -> File.separator;
            case ":"  -> File.pathSeparator;
            case "~"  -> System.getProperty("user.home");
            default   -> resolvePathToken(content, projectRoot);
        };
    }

    private String resolvePathToken(String content, Path projectRoot) {
        String home = System.getProperty("user.home");

        // Multi-path token: contains ":" separators with path-like segments
        if (isMultiPath(content)) {
            return Arrays.stream(content.split(":"))
                .map(part -> normalizePath(part.trim(), projectRoot, home))
                .collect(Collectors.joining(File.pathSeparator));
        }

        return normalizePath(content, projectRoot, home);
    }

    private boolean isMultiPath(String content) {
        // Has a ":" that separates path segments (not a Windows drive letter like C:)
        return content.matches(".*[./~][^:]*:.*") || content.contains(":./") || content.contains(":~/");
    }

    private String normalizePath(String path, Path projectRoot, String home) {
        if (path.startsWith("~/")) {
            return home + File.separator + path.substring(2).replace("/", File.separator);
        }
        if (path.startsWith("./") || path.startsWith(".\\")) {
            return projectRoot.resolve(path.substring(2))
                .normalize()
                .toString();
        }
        return path.replace("/", File.separator);
    }
}
