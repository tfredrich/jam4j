package com.strategicgains.jam4j.model;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PomReader {

    private final Model model;
    private final Map<String, String> dependencies = new LinkedHashMap<>();
    private final Map<String, String> devDependencies = new LinkedHashMap<>();

    private PomReader(Model model) {
        this.model = model;

        for (Dependency dep : model.getDependencies()) {
            String scope = dep.getScope();
            String ga = dep.getGroupId() + ":" + dep.getArtifactId();
            String version = dep.getVersion();

            if (version == null) continue;

            if (scope == null || scope.equals("compile") || scope.equals("runtime")) {
                dependencies.put(ga, version);
            } else {
                devDependencies.put(ga, version);
            }
        }
    }

    public static PomReader from(File pomFile) throws Exception {
        Model model = new MavenXpp3Reader().read(new FileReader(pomFile));
        return new PomReader(model);
    }

    public Map<String, String> getDependencies() {
        return dependencies;
    }

    public Map<String, String> getDevDependencies() {
        return devDependencies;
    }

    public List<String> allCoords() {
        List<String> coords = new ArrayList<>();
        for (Map.Entry<String, String> e : dependencies.entrySet()) {
            coords.add(e.getKey() + ":" + e.getValue());
        }
        for (Map.Entry<String, String> e : devDependencies.entrySet()) {
            coords.add(e.getKey() + ":" + e.getValue());
        }
        return coords;
    }

    public String getName() {
        String name = model.getArtifactId();
        return name != null ? name : model.getName();
    }

    public String getVersion() {
        return model.getVersion();
    }
}
