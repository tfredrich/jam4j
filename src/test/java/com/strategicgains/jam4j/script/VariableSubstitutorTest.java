package com.strategicgains.jam4j.script;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VariableSubstitutorTest {

    private final VariableSubstitutor sub = new VariableSubstitutor();
    private final Path root = Path.of("/project");
    private final String cp = "/home/user/.m2/a.jar" + File.pathSeparator + "/home/user/.m2/b.jar";

    @Test
    void replacesDeps() {
        String result = sub.substitute("javac -cp {{deps}} Main.java", cp, root);
        assertThat(result).isEqualTo("javac -cp " + cp + " Main.java");
    }

    @Test
    void replacesFileSeparator() {
        String result = sub.substitute("src{/}main{/}java", cp, root);
        assertThat(result).isEqualTo("src" + File.separator + "main" + File.separator + "java");
    }

    @Test
    void replacesPathSeparator() {
        String result = sub.substitute("a.jar{:}b.jar", cp, root);
        assertThat(result).isEqualTo("a.jar" + File.pathSeparator + "b.jar");
    }

    @Test
    void replacesHomeDir() {
        String home = System.getProperty("user.home");
        String result = sub.substitute("{~}/.m2/settings.xml", cp, root);
        assertThat(result).startsWith(home);
    }

    @Test
    void replacesRelativePath() {
        String result = sub.substitute("-d {./target/classes}", cp, root);
        assertThat(result).contains("target" + File.separator + "classes");
        assertThat(result).doesNotContain("{./");
    }

    @Test
    void replacesMultipleVariablesInOneScript() {
        String script = "javac -cp {{deps}}{:}{./target/classes} -d {./target} src{/}*.java";
        String result = sub.substitute(script, cp, root);
        assertThat(result).doesNotContain("{{deps}}");
        assertThat(result).doesNotContain("{/}");
        assertThat(result).doesNotContain("{:}");
        assertThat(result).doesNotContain("{./");
        assertThat(result).contains(cp);
    }

    @Test
    void noSubstitutionWhenNoTokens() {
        String script = "echo hello world";
        assertThat(sub.substitute(script, cp, root)).isEqualTo(script);
    }

    @Test
    void tokenizeSimple() {
        var tokens = ScriptRunner.tokenize("javac -cp foo.jar Main.java");
        assertThat(tokens).containsExactly("javac", "-cp", "foo.jar", "Main.java");
    }

    @Test
    void tokenizeWithDoubleQuotes() {
        var tokens = ScriptRunner.tokenize("echo \"hello world\"");
        assertThat(tokens).containsExactly("echo", "hello world");
    }

    @Test
    void tokenizeWithSingleQuotes() {
        var tokens = ScriptRunner.tokenize("sh -c 'echo foo'");
        assertThat(tokens).containsExactly("sh", "-c", "echo foo");
    }

    @Test
    void tokenizeHandlesLeadingAndTrailingSpaces() {
        var tokens = ScriptRunner.tokenize("  javac   Main.java  ");
        assertThat(tokens).containsExactly("javac", "Main.java");
    }
}
