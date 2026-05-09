package com.strategicgains.jam4j.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VariableSubstitutorTest {

    private final VariableSubstitutor sub = new VariableSubstitutor();
    private final Path root = Path.of("/project");
    private final String prodCp = "/home/user/.m2/a.jar" + File.pathSeparator + "/home/user/.m2/b.jar";
    private final String devCp = prodCp + File.pathSeparator + "/home/user/.m2/test.jar";
    private final ScriptVariables vars = new ScriptVariables(prodCp, devCp, root.resolve("src/main/java"), root.resolve("src/test/java"), "com.example.Main");

    @Test
    void replacesDepsWithProdClasspath() {
        String result = sub.substitute("javac -cp {{deps}} Main.java", vars, root);
        assertThat(result).isEqualTo("javac -cp " + prodCp + " Main.java");
    }

    @Test
    void replacesDevDepsWithFullClasspath() {
        String result = sub.substitute("java -cp {{deps:dev}} TestRunner", vars, root);
        assertThat(result).isEqualTo("java -cp " + devCp + " TestRunner");
    }

    @Test
    void replacesSourcesWithJavaFiles(@TempDir Path tempDir) throws IOException {
        Path srcDir = Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        Files.writeString(srcDir.resolve("Foo.java"), "class Foo {}");
        Files.writeString(srcDir.resolve("Bar.java"), "class Bar {}");
        ScriptVariables v = new ScriptVariables(prodCp, devCp, tempDir.resolve("src/main/java"), tempDir.resolve("src/test/java"), null);

        String result = sub.substitute("javac {{sources}}", v, tempDir);
        assertThat(result).startsWith("javac ");
        assertThat(result).contains("Bar.java");
        assertThat(result).contains("Foo.java");
    }

    @Test
    void replacesTestsWithJavaFiles(@TempDir Path tempDir) throws IOException {
        Path testDir = Files.createDirectories(tempDir.resolve("src/test/java/com/example"));
        Files.writeString(testDir.resolve("FooTest.java"), "class FooTest {}");
        ScriptVariables v = new ScriptVariables(prodCp, devCp, tempDir.resolve("src/main/java"), tempDir.resolve("src/test/java"), null);

        String result = sub.substitute("javac {{tests}}", v, tempDir);
        assertThat(result).contains("FooTest.java");
    }

    @Test
    void returnsEmptyForMissingSourceDir(@TempDir Path tempDir) {
        ScriptVariables v = new ScriptVariables(prodCp, devCp, tempDir.resolve("nonexistent"), tempDir.resolve("also-nonexistent"), null);
        assertThat(sub.substitute("javac {{sources}}", v, tempDir)).isEqualTo("javac ");
        assertThat(sub.substitute("javac {{tests}}", v, tempDir)).isEqualTo("javac ");
    }

    @Test
    void replacesMainClass() {
        String result = sub.substitute("java -cp {{deps}} {{main}}", vars, root);
        assertThat(result).isEqualTo("java -cp " + prodCp + " com.example.Main");
    }

    @Test
    void replacesMainClassWithEmptyWhenNull() {
        ScriptVariables v = new ScriptVariables(prodCp, devCp, root.resolve("src/main/java"), root.resolve("src/test/java"), null);
        String result = sub.substitute("java {{main}}", v, root);
        assertThat(result).isEqualTo("java ");
    }

    @Test
    void replacesFileSeparator() {
        String result = sub.substitute("src{/}main{/}java", vars, root);
        assertThat(result).isEqualTo("src" + File.separator + "main" + File.separator + "java");
    }

    @Test
    void replacesPathSeparator() {
        String result = sub.substitute("a.jar{:}b.jar", vars, root);
        assertThat(result).isEqualTo("a.jar" + File.pathSeparator + "b.jar");
    }

    @Test
    void replacesHomeDir() {
        String home = System.getProperty("user.home");
        String result = sub.substitute("{~}/.m2/settings.xml", vars, root);
        assertThat(result).startsWith(home);
    }

    @Test
    void replacesRelativePath() {
        String result = sub.substitute("-d {./target/classes}", vars, root);
        assertThat(result).contains("target" + File.separator + "classes");
        assertThat(result).doesNotContain("{./");
    }

    @Test
    void replacesMultipleVariablesInOneScript() {
        String script = "javac -cp {{deps}}{:}{./target/classes} -d {./target} src{/}*.java";
        String result = sub.substitute(script, vars, root);
        assertThat(result).doesNotContain("{{deps}}");
        assertThat(result).doesNotContain("{/}");
        assertThat(result).doesNotContain("{:}");
        assertThat(result).doesNotContain("{./");
        assertThat(result).contains(prodCp);
    }

    @Test
    void noSubstitutionWhenNoTokens() {
        String script = "echo hello world";
        assertThat(sub.substitute(script, vars, root)).isEqualTo(script);
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
