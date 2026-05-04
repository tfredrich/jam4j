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
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ArtifactResolver {

    private static final RemoteRepository MAVEN_CENTRAL = new RemoteRepository.Builder(
        "central", "default", "https://repo1.maven.org/maven2/").build();

    private static final String SEARCH_API = "https://search.maven.org/solrsearch/select";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RepositorySystem system;
    private final RepositorySystemSession session;

    public ArtifactResolver(Path cacheDir) {
        this.system = ResolverFactory.newRepositorySystem();
        this.session = ResolverFactory.newSession(system, cacheDir);
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
        String url = SEARCH_API + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&rows=" + maxResults + "&wt=json";

        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Search API returned HTTP " + response.statusCode());
        }

        return parseSearchResults(response.body());
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

    public record SearchResult(String groupId, String artifactId, String latestVersion, String repositoryId) {
        /** Returns the canonical install coordinate: group:artifact:version */
        public String coord() {
            return groupId + ":" + artifactId + ":" + latestVersion;
        }
    }
}
