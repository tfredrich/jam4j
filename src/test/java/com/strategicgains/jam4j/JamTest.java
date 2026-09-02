package com.strategicgains.jam4j;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JamTest {

    @Test
    void formatsVersionWithBuildTime() {
        assertThat(Jam.formatVersion("1.0.0-SNAPSHOT", "2026-05-04T14:30:12Z"))
            .isEqualTo("jam 1.0.0-SNAPSHOT (build 2026-05-04T14:30:12Z)");
    }

    @Test
    void fallsBackToDevVersionWhenMetadataIsUnavailable() {
        assertThat(Jam.formatVersion(null, null)).isEqualTo("jam dev");
    }

    @Test
    void rootVersionOptionUsesVersionProvider() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = new CommandLine(new Jam());
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute("--version");

        assertThat(exitCode).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8)).startsWith("jam ");
    }

    @Test
    void rootCommandRegistersUpgrade() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = new CommandLine(new Jam());
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute("upgrade", "--help");

        assertThat(exitCode).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Upgrade the local jam installation");
    }

    @Test
    void parsesGlobalOptionsBeforeACommandChain() {
        Jam.Invocation invocation = Jam.Invocation.parse(new String[] {
            "--quiet", "--project", "app.json", "clean", "build", "package"
        });

        assertThat(invocation.globalArguments())
            .containsExactly("--quiet", "--project", "app.json");
        assertThat(invocation.commands())
            .containsExactly("clean", "build", "package");
        assertThat(invocation.commandsAsArguments())
            .containsExactly(
                java.util.List.of("clean"),
                java.util.List.of("build"),
                java.util.List.of("package"));
    }

    @Test
    void rejectsGlobalOptionsAfterTheCommandBegins() {
        assertThatThrownBy(() -> Jam.Invocation.parse(new String[] {"clean", "--quiet"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("global option must appear before the command: --quiet");
    }

    @Test
    void upgradeCannotBeChained() {
        assertThat(Jam.execute(new String[] {"upgrade", "clean"})).isEqualTo(2);
        assertThat(Jam.execute(new String[] {"clean", "upgrade"})).isEqualTo(2);
    }

    @Test
    void globalQuietOptionCanPrecedeStandaloneUpgrade() {
        assertThat(Jam.execute(new String[] {"--quiet", "upgrade", "--help"})).isZero();
    }

    @Test
    void everyCommandAcceptsHelpWithoutExecuting() {
        for (String command : new String[] {
            "init", "search", "install", "path", "run", "build", "test", "clean", "package", "upgrade"
        }) {
            for (String help : new String[] {"-h", "--help"}) {
                assertThat(Jam.execute(new String[] {command, help}))
                    .as("%s for %s", help, command)
                    .isZero();
            }
        }
    }
}
