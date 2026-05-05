package com.strategicgains.jam4j;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

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
}
