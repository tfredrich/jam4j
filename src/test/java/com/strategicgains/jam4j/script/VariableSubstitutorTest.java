package com.strategicgains.jam4j.script;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VariableSubstitutorTest {

    private final VariableSubstitutor sub = new VariableSubstitutor();
    private final Path root = Path.of("/project");
    private final String prodCp = "/home/user/.m2/a.jar" + File.pathSeparator + "/home/user/.m2/b.jar";
    private final String devCp = prodCp + File.pathSeparator + "/home/user/.m2/test.jar";

    @Test
    void replacesDepsWithProdClasspath() {
        String result = sub.substitute("javac -cp {{deps}} Main.java", prodCp, devCp, root);
        assertThat(result).isEqualTo("javac -cp " + prodCp + " Main.java");
    }

    @Test
    void replacesDevDepsWithFullClasspath() {
        String result = sub.substitute("java -cp {{deps:dev}} TestRunner", prodCp, devCp, root);
        assertThat(result).isEqualTo("java -cp " + devCp + " TestRunner");
    }

    @Test
    void replacesFileSeparator() {
        String result = sub.substitute("src{/}main{/}java", prodCp, devCp, root);
        assertThat(result).isEqualTo("src" + File.separator + "main" + File.separator + "java");
    }

    @Test
    void replacesPathSeparator() {
        String result = sub.substitute("a.jar{:}b.jar", prodCp, devCp, root);
        assertThat(result).isEqualTo("a.jar" + File.pathSeparator + "b.jar");
    }

    @Test
    void replacesHomeDir() {
        String home = System.getProperty("user.home");
        String result = sub.substitute("{~}/.m2/settings.xml", prodCp, devCp, root);
        assertThat(result).startsWith(home);
    }

    @Test
    void replacesRelativePath() {
        String result = sub.substitute("-d {./target/classes}", prodCp, devCp, root);
        assertThat(result).contains("target" + File.separator + "classes");
        assertThat(result).doesNotContain("{./");
    }

    @Test
    void replacesMultipleVariablesInOneScript() {
        String script = "javac -cp {{deps}}{:}{./target/classes} -d {./target} src{/}*.java";
        String result = sub.substitute(script, prodCp, devCp, root);
        assertThat(result).doesNotContain("{{deps}}");
        assertThat(result).doesNotContain("{/}");
        assertThat(result).doesNotContain("{:}");
        assertThat(result).doesNotContain("{./");
        assertThat(result).contains(prodCp);
    }

    @Test
    void noSubstitutionWhenNoTokens() {
        String script = "echo hello world";
        assertThat(sub.substitute(script, prodCp, devCp, root)).isEqualTo(script);
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
