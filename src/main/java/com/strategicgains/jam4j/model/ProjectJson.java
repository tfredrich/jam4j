package com.strategicgains.jam4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectJson {

    public String name;
    public String version;

    @JsonProperty("main")
    public String mainClass;

    public Map<String, String> dependencies = new LinkedHashMap<>();
    public Map<String, String> devDependencies = new LinkedHashMap<>();
    public Map<String, String> scripts = new LinkedHashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public static ProjectJson load(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), ProjectJson.class);
    }

    public void save(Path path) throws IOException {
        MAPPER.writeValue(path.toFile(), this);
    }

    /** "group:artifact": "version" entries → ["group:artifact:version", ...] */
    public List<String> dependencyCoords() {
        return toCoords(dependencies);
    }

    public List<String> devDependencyCoords() {
        return toCoords(devDependencies);
    }

    public List<String> allDependencyCoords() {
        List<String> all = new ArrayList<>(dependencyCoords());
        all.addAll(devDependencyCoords());
        return all;
    }

    public void addDependency(String groupArtifact, String version) {
        dependencies.put(groupArtifact, version);
    }

    private static List<String> toCoords(Map<String, String> deps) {
        List<String> coords = new ArrayList<>();
        for (Map.Entry<String, String> entry : deps.entrySet()) {
            coords.add(entry.getKey() + ":" + entry.getValue());
        }
        return coords;
    }
}
