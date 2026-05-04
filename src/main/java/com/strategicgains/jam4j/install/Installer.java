package com.strategicgains.jam4j.install;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class Installer {

    /**
     * Install resolved JAR files into libDir. Prefers symlinks (like node_modules) for
     * disk efficiency; falls back to copy when symlinks are unsupported (e.g. Windows FAT,
     * cross-filesystem) or when localOnly is set.
     */
    public void install(List<File> jars, Path libDir, boolean localOnly, boolean quiet)
            throws IOException {

        Files.createDirectories(libDir);

        for (File jar : jars) {
            Path target = libDir.resolve(jar.getName());

            if (Files.exists(target) || Files.isSymbolicLink(target)) {
                if (!quiet) System.out.println("  Already installed: " + target.getFileName());
                continue;
            }

            if (!localOnly) {
                try {
                    Files.createSymbolicLink(target, jar.toPath().toAbsolutePath());
                    if (!quiet) System.out.println("  Linked:  " + target + " -> " + jar.getAbsolutePath());
                    continue;
                } catch (UnsupportedOperationException | IOException ignored) {
                    // Fall through to copy
                }
            }

            Files.copy(jar.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            if (!quiet) System.out.println("  Copied:  " + target);
        }
    }
}
