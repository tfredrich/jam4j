package com.strategicgains.jam4j.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PackageCommandTest {

    private PackageCommand cmd() {
        PackageCommand cmd = new PackageCommand();
        cmd.opts = new CommonOptions();
        return cmd;
    }

    @Test
    void hasClassFilesReturnsFalseWhenDirectoryDoesNotExist(@TempDir Path tmp) {
        assertThat(cmd().hasClassFiles(tmp.resolve("nonexistent"))).isFalse();
    }

    @Test
    void hasClassFilesReturnsFalseWhenDirectoryIsEmpty(@TempDir Path tmp) {
        assertThat(cmd().hasClassFiles(tmp)).isFalse();
    }

    @Test
    void hasClassFilesReturnsTrueWhenClassFileIsPresent(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("Main.class"), "");
        assertThat(cmd().hasClassFiles(tmp)).isTrue();
    }

    @Test
    void hasClassFilesReturnsTrueWhenClassFileIsInSubdirectory(@TempDir Path tmp) throws IOException {
        Path sub = Files.createDirectories(tmp.resolve("com/example"));
        Files.writeString(sub.resolve("Main.class"), "");
        assertThat(cmd().hasClassFiles(tmp)).isTrue();
    }
}
