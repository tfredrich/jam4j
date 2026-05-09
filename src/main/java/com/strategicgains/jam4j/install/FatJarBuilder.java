package com.strategicgains.jam4j.install;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;

public class FatJarBuilder {

    /**
     * Build a fat JAR by merging dependency JARs with the project's compiled classes.
     *
     * META-INF/services/* entries are concatenated so SPI registrations from all
     * JARs survive. Signature files are stripped so the repacked JAR remains valid.
     * For all other duplicate entries, the first-encountered value wins.
     */
    public void build(List<File> dependencyJars, Path classesDir, Path outputJar, String mainClass, boolean quiet)
            throws IOException {

        Files.createDirectories(outputJar.getParent());

        Map<String, byte[]> entries = new LinkedHashMap<>();
        Map<String, List<byte[]>> serviceEntries = new LinkedHashMap<>();

        for (File depJar : dependencyJars) {
            if (!quiet) System.out.println("  Merging: " + depJar.getName());
            mergeJar(depJar, entries, serviceEntries);
        }

        // Project classes always override dependency entries with the same path
        if (Files.isDirectory(classesDir)) {
            try (var stream = Files.walk(classesDir)) {
                stream.filter(p -> !Files.isDirectory(p)).forEach(p -> {
                    String name = classesDir.relativize(p).toString().replace(File.separatorChar, '/');
                    try {
                        entries.put(name, Files.readAllBytes(p));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) attrs.put(Attributes.Name.MAIN_CLASS, mainClass);

        if (!quiet) System.out.println("  Writing: " + outputJar);

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(outputJar), manifest)) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
            for (var entry : serviceEntries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                for (byte[] chunk : entry.getValue()) {
                    out.write(chunk);
                    // Ensure each provider list ends with a newline before the next is appended
                    if (chunk.length > 0 && chunk[chunk.length - 1] != '\n') out.write('\n');
                }
                out.closeEntry();
            }
        }
    }

    private void mergeJar(File jar, Map<String, byte[]> entries, Map<String, List<byte[]>> serviceEntries)
            throws IOException {

        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> e = jf.entries();
            while (e.hasMoreElements()) {
                JarEntry entry = e.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (name.equals("META-INF/MANIFEST.MF")) continue;
                if (isSignatureFile(name)) continue;

                byte[] bytes = jf.getInputStream(entry).readAllBytes();

                if (name.startsWith("META-INF/services/")) {
                    serviceEntries.computeIfAbsent(name, k -> new ArrayList<>()).add(bytes);
                } else {
                    entries.putIfAbsent(name, bytes);
                }
            }
        }
    }

    private boolean isSignatureFile(String name) {
        if (!name.startsWith("META-INF/")) return false;
        String base = name.substring("META-INF/".length());
        return base.endsWith(".SF") || base.endsWith(".RSA") || base.endsWith(".DSA") || base.endsWith(".EC");
    }
}
