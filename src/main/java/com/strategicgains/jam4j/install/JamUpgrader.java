package com.strategicgains.jam4j.install;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

public class JamUpgrader {

    static final URI LATEST_RELEASE = URI.create(
        "https://api.github.com/repos/tfredrich/jam4j/releases/latest");
    private static final String JAR_PREFIX = "jam4j-";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI latestReleaseUri;

    public JamUpgrader() {
        this(HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(), new ObjectMapper());
    }

    JamUpgrader(HttpClient httpClient, ObjectMapper objectMapper) {
        this(httpClient, objectMapper, LATEST_RELEASE);
    }

    JamUpgrader(HttpClient httpClient, ObjectMapper objectMapper, URI latestReleaseUri) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.latestReleaseUri = latestReleaseUri;
    }

    public boolean upgrade(Path jamHome, String currentVersion) throws IOException, InterruptedException {
        Path installedJar = jamHome.resolve("bin").resolve("jam4j.jar");
        if (!Files.isRegularFile(installedJar)) {
            throw new IOException("installed JAR not found at " + installedJar);
        }

        Release release = latestRelease();
        if (currentVersion == null || currentVersion.isBlank()) {
            throw new IOException("cannot determine the installed version");
        }

        String latestVersion = release.version();
        if (SemanticVersion.compare(currentVersion, latestVersion) >= 0) {
            System.out.println("jam is already up to date (" + currentVersion + ")");
            return true;
        }

        String jarName = JAR_PREFIX + latestVersion + ".jar";
        String checksumName = jarName + ".sha256";
        URI jarUri = release.assetUrl(jarName);
        URI checksumUri = release.assetUrl(checksumName);
        if (jarUri == null || checksumUri == null) {
            throw new IOException("release " + latestVersion + " is missing " + jarName
                + " or " + checksumName);
        }

        System.out.println("Updating jam " + currentVersion + " -> " + latestVersion + "...");
        Path temporaryJar = Files.createTempFile(installedJar.getParent(), ".jam4j-", ".jar");
        try {
            download(jarUri, temporaryJar);
            String expectedChecksum = downloadText(checksumUri).trim().split("\\s+")[0];
            verifyChecksum(temporaryJar, expectedChecksum);
            replace(installedJar, temporaryJar);
        } finally {
            Files.deleteIfExists(temporaryJar);
        }

        System.out.println("Updated jam to " + latestVersion + ".");
        return true;
    }

    private Release latestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(latestReleaseUri)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "jam4j-updater")
            .timeout(HTTP_TIMEOUT)
            .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response.statusCode(), "latest release lookup");
        JsonNode root = objectMapper.readTree(response.body());
        if (root.path("draft").asBoolean() || root.path("prerelease").asBoolean()) {
            throw new IOException("latest GitHub release is not stable");
        }

        String tag = root.path("tag_name").asText();
        if (tag.isBlank()) throw new IOException("latest GitHub release has no tag");
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        Release release = new Release(version);
        for (JsonNode asset : root.path("assets")) {
            release.addAsset(asset.path("name").asText(), asset.path("browser_download_url").asText());
        }
        return release;
    }

    private void download(URI uri, Path destination) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(HTTP_TIMEOUT).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        ensureSuccess(response.statusCode(), "download");
        Files.write(destination, response.body());
    }

    private String downloadText(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(HTTP_TIMEOUT).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response.statusCode(), "checksum download");
        return response.body();
    }

    private static void verifyChecksum(Path file, String expected) throws IOException {
        if (!expected.matches("(?i)[0-9a-f]{64}")) throw new IOException("invalid SHA-256 checksum file");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            String actual = HexFormat.of().formatHex(digest);
            if (!actual.equalsIgnoreCase(expected)) throw new IOException("checksum verification failed");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static void replace(Path installedJar, Path temporaryJar) throws IOException {
        try {
            Files.move(temporaryJar, installedJar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporaryJar, installedJar, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureSuccess(int status, String operation) throws IOException {
        if (status < 200 || status >= 300) throw new IOException(operation + " failed (HTTP " + status + ")");
    }

    static final class Release {
        private final String version;
        private final java.util.Map<String, URI> assets = new java.util.HashMap<>();

        Release(String version) { this.version = version; }
        String version() { return version; }
        void addAsset(String name, String url) { if (!name.isBlank() && !url.isBlank()) assets.put(name, URI.create(url)); }
        URI assetUrl(String name) { return assets.get(name); }
    }

    static final class SemanticVersion {
        static int compare(String left, String right) {
            return parse(left).compareTo(parse(right));
        }

        private static Version parse(String value) {
            String normalized = value.startsWith("v") ? value.substring(1) : value;
            String[] parts = normalized.split("[-+](?=[^+]*$)", 2);
            String[] numbers = parts[0].split("\\.");
            if (numbers.length < 2 || numbers.length > 3) throw new IllegalArgumentException("invalid version: " + value);
            int major = Integer.parseInt(numbers[0]);
            int minor = Integer.parseInt(numbers[1]);
            int patch = numbers.length == 3 ? Integer.parseInt(numbers[2]) : 0;
            return new Version(major, minor, patch, parts.length == 2 ? parts[1] : null);
        }

        private record Version(int major, int minor, int patch, String prerelease) implements Comparable<Version> {
            @Override public int compareTo(Version other) {
                int result = Integer.compare(major, other.major); if (result != 0) return result;
                result = Integer.compare(minor, other.minor); if (result != 0) return result;
                result = Integer.compare(patch, other.patch); if (result != 0) return result;
                if (prerelease == null) return other.prerelease == null ? 0 : 1;
                if (other.prerelease == null) return -1;
                return prerelease.compareTo(other.prerelease);
            }
        }
    }
}
