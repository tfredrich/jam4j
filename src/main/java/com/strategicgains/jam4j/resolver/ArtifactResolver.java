package com.strategicgains.jam4j.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

public class ArtifactResolver {

    static final String MAVEN_CENTRAL_URL = "https://repo.maven.apache.org/maven2/";

    private static final RemoteRepository MAVEN_CENTRAL = new RemoteRepository.Builder(
        "central", "default", MAVEN_CENTRAL_URL).build();

    private static final String SEARCH_API = "https://search.maven.org/solrsearch/select";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final Path cacheDir;

    public ArtifactResolver(Path cacheDir) {
        this(cacheDir, false, false);
    }

    public ArtifactResolver(Path cacheDir, boolean ignorePomRepositories, boolean verboseTransfers) {
        this.cacheDir = cacheDir;
        this.system = ResolverFactory.newRepositorySystem();
        this.session = ResolverFactory.newSession(system, cacheDir, ignorePomRepositories,
            verboseTransfers ? new VerboseTransferListener() : null);
    }

    /**
     * Resolve a list of artifact coordinates (group:artifact:version) plus their
     * transitive compile-scope dependencies. Returns local JAR files.
     */
    public List<File> resolve(List<String> coords, List<String> extraRepoSpecs)
            throws DependencyResolutionException {

        List<RemoteRepository> repos = buildRepos(extraRepoSpecs);

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRepositories(repos);

        for (String coord : coords) {
            Artifact artifact = new DefaultArtifact(normalizeCoord(coord));
            collectRequest.addDependency(new Dependency(artifact, JavaScopes.COMPILE));
        }

        DependencyRequest dependencyRequest = new DependencyRequest(
            collectRequest,
            DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE));

        DependencyResult result = system.resolveDependencies(session, dependencyRequest);

        List<File> files = new ArrayList<>();
        for (var ar : result.getArtifactResults()) {
            File f = ar.getArtifact().getFile();
            if (f != null) files.add(f);
        }
        return files;
    }

    /**
     * Search Maven Central for artifacts matching the query.
     * Uses the Maven Central Search REST API (Solr-based).
     */
    public List<SearchResult> search(String query, int maxResults) throws Exception {
        return search(query, new SearchOptions(maxResults, false, false, true, List.of()));
    }

    public List<SearchResult> search(String query, SearchOptions options) throws Exception {
        Map<String, SearchResult> results = new LinkedHashMap<>();
        if (options.includeCentral()) {
            for (SearchResult result : searchMavenCentral(query, options.maxResults())) {
                addResult(results, result, options.includeSnapshots());
            }
        }

        if (options.includeLocal()) {
            for (SearchResult result : searchLocalRepository(query)) {
                addResult(results, result, options.includeSnapshots());
            }
        }

        Optional<GroupArtifact> groupArtifact = GroupArtifact.from(query);
        if (groupArtifact.isPresent()) {
            for (String repoSpec : options.extraRepoSpecs()) {
                for (SearchResult result : searchRepositoryMetadata(groupArtifact.get(), repoSpec)) {
                    addResult(results, result, options.includeSnapshots());
                }
            }
        }

        return results.values().stream()
            .limit(options.maxResults())
            .toList();
    }

    private List<SearchResult> searchMavenCentral(String query, int maxResults) throws Exception {
        String url = SEARCH_API + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&rows=" + maxResults + "&wt=json";

        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Search API returned HTTP " + response.statusCode());
        }

        return parseSearchResults(response.body());
    }

    private List<SearchResult> searchLocalRepository(String query) throws IOException {
        if (!Files.isDirectory(cacheDir)) {
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        try (var paths = Files.walk(cacheDir)) {
            paths.filter(Files::isDirectory)
                .map(this::localSearchResult)
                .flatMap(Optional::stream)
                .filter(result -> matches(query, result))
                .forEach(results::add);
        }
        return results;
    }

    private Optional<SearchResult> localSearchResult(Path versionDir) {
        Path artifactDir = versionDir.getParent();
        if (artifactDir == null) {
            return Optional.empty();
        }

        String artifactId = artifactDir.getFileName().toString();
        String version = versionDir.getFileName().toString();
        if (artifactId.isBlank() || version.isBlank()) {
            return Optional.empty();
        }

        String expectedPrefix = artifactId + "-" + version;
        try (var files = Files.list(versionDir)) {
            boolean hasArtifact = files
                .map(path -> path.getFileName().toString())
                .anyMatch(name -> name.startsWith(expectedPrefix) && (name.endsWith(".jar") || name.endsWith(".pom")));
            if (!hasArtifact) {
                return Optional.empty();
            }
        } catch (IOException e) {
            return Optional.empty();
        }

        Path groupPath = cacheDir.relativize(artifactDir).getParent();
        if (groupPath == null) {
            return Optional.empty();
        }
        String groupId = groupPath.toString().replace(File.separatorChar, '.');
        return Optional.of(new SearchResult(groupId, artifactId, version, "local"));
    }

    private List<SearchResult> searchRepositoryMetadata(GroupArtifact artifact, String repoSpec) throws Exception {
        RepositorySpec repository = RepositorySpec.from(repoSpec);
        String metadataUrl = repository.url()
            + artifact.groupId().replace('.', '/')
            + "/" + artifact.artifactId()
            + "/maven-metadata.xml";

        HttpRequest.Builder request = HttpRequest.newBuilder()
            .uri(URI.create(metadataUrl))
            .header("Accept", "application/xml")
            .GET();
        repository.authorization().ifPresent(auth -> request.header("Authorization", auth));

        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
        HttpResponse<String> response = client.send(request.timeout(REQUEST_TIMEOUT).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return List.of();
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException(repository.id() + " metadata returned HTTP " + response.statusCode());
        }

        List<String> versions = parseMetadataVersions(response.body());
        List<SearchResult> results = new ArrayList<>();
        for (int i = versions.size() - 1; i >= 0; i--) {
            results.add(new SearchResult(artifact.groupId(), artifact.artifactId(), versions.get(i), repository.id()));
        }
        return results;
    }

    static List<String> parseMetadataVersions(String metadataXml) throws Exception {
        Element root = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new java.io.ByteArrayInputStream(metadataXml.getBytes(StandardCharsets.UTF_8)))
            .getDocumentElement();
        var versionNodes = root.getElementsByTagName("version");
        List<String> versions = new ArrayList<>();
        for (int i = 0; i < versionNodes.getLength(); i++) {
            String version = versionNodes.item(i).getTextContent();
            if (version != null && !version.isBlank()) {
                versions.add(version.trim());
            }
        }
        return versions;
    }

    private boolean matches(String query, SearchResult result) {
        String normalizedQuery = query.toLowerCase();
        return result.groupId().toLowerCase().contains(normalizedQuery)
            || result.artifactId().toLowerCase().contains(normalizedQuery)
            || result.coord().toLowerCase().contains(normalizedQuery);
    }

    private void addResult(Map<String, SearchResult> results, SearchResult result, boolean includeSnapshots) {
        if (!includeSnapshots && result.version().endsWith("-SNAPSHOT")) {
            return;
        }
        results.putIfAbsent(result.coord(), result);
    }

    private List<RemoteRepository> buildRepos(List<String> extraRepoSpecs) {
        List<RemoteRepository> repos = new ArrayList<>();
        repos.add(MAVEN_CENTRAL);
        for (String spec : extraRepoSpecs) {
            repos.add(parseRepoSpec(spec));
        }
        return repos;
    }

    /** Parse "name=url" or bare "url" into a RemoteRepository, with optional env-var auth. */
    private RemoteRepository parseRepoSpec(String spec) {
        int eq = spec.indexOf('=');
        if (eq > 0 && !spec.startsWith("http")) {
            String name = spec.substring(0, eq);
            String url = spec.substring(eq + 1);
            RemoteRepository.Builder builder = new RemoteRepository.Builder(name, "default", url);
            String user = System.getenv("JAM_REPO_" + name.toUpperCase() + "_USER");
            String pass = System.getenv("JAM_REPO_" + name.toUpperCase() + "_PASSWORD");
            if (user != null && pass != null) {
                builder.setAuthentication(
                    new AuthenticationBuilder().addUsername(user).addPassword(pass).build());
            }
            return builder.build();
        }
        return new RemoteRepository.Builder(
            "extra-" + Math.abs(spec.hashCode()), "default", spec).build();
    }

    /** Validate and normalize a coordinate string. DefaultArtifact handles g:a:v, g:a:type:v, g:a:type:classifier:v. */
    private String normalizeCoord(String coord) {
        String[] parts = coord.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                "Invalid artifact coordinate (expected group:artifact:version): " + coord);
        }
        return coord;
    }

    private List<SearchResult> parseSearchResults(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode docs = root.path("response").path("docs");
        List<SearchResult> results = new ArrayList<>();
        for (JsonNode doc : docs) {
            results.add(new SearchResult(
                doc.path("g").asText(""),
                doc.path("a").asText(""),
                doc.path("latestVersion").asText(""),
                doc.path("repositoryId").asText("")
            ));
        }
        return results;
    }

    public record SearchOptions(
        int maxResults,
        boolean includeSnapshots,
        boolean includeLocal,
        boolean includeCentral,
        List<String> extraRepoSpecs) {
    }

    public record SearchResult(String groupId, String artifactId, String version, String repositoryId) {
        /** Returns the canonical install coordinate: group:artifact:version */
        public String coord() {
            return groupId + ":" + artifactId + ":" + version;
        }
    }

    private static class VerboseTransferListener extends AbstractTransferListener {

        @Override
        public void transferInitiated(TransferEvent event) {
            TransferResource resource = event.getResource();
            System.err.println("Downloading from " + resource.getRepositoryId() + ": "
                + resource.getRepositoryUrl() + resource.getResourceName());
        }

        @Override
        public void transferFailed(TransferEvent event) {
            TransferResource resource = event.getResource();
            Exception exception = event.getException();
            String message = exception == null ? "unknown error" : exception.getMessage();
            System.err.println("Failed from " + resource.getRepositoryId() + ": "
                + resource.getRepositoryUrl() + resource.getResourceName() + " (" + message + ")");
        }
    }

    private record GroupArtifact(String groupId, String artifactId) {
        private static Optional<GroupArtifact> from(String query) {
            String[] parts = query.split(":");
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new GroupArtifact(parts[0], parts[1]));
        }
    }

    private record RepositorySpec(String id, String url, Optional<String> authorization) {
        private static RepositorySpec from(String spec) {
            int eq = spec.indexOf('=');
            if (eq > 0 && !spec.startsWith("http")) {
                String id = spec.substring(0, eq);
                return new RepositorySpec(id, ensureTrailingSlash(spec.substring(eq + 1)), authHeader(id));
            }
            return new RepositorySpec("extra-" + Math.abs(spec.hashCode()), ensureTrailingSlash(spec), Optional.empty());
        }

        private static Optional<String> authHeader(String id) {
            String user = System.getenv("JAM_REPO_" + id.toUpperCase() + "_USER");
            String pass = System.getenv("JAM_REPO_" + id.toUpperCase() + "_PASSWORD");
            if (user == null || pass == null) {
                return Optional.empty();
            }
            String credentials = java.util.Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
            return Optional.of("Basic " + credentials);
        }

        private static String ensureTrailingSlash(String url) {
            return url.endsWith("/") ? url : url + "/";
        }
    }
}
