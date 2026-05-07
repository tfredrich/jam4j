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
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PomReader {

    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{([^}]+)}");

    private final Model model;
    private final Map<String, String> dependencies = new LinkedHashMap<>();
    private final Map<String, String> devDependencies = new LinkedHashMap<>();

    private PomReader(Model model) {
        this.model = model;
        Properties props = model.getProperties();

        for (Dependency dep : model.getDependencies()) {
            String scope = dep.getScope();
            String ga = dep.getGroupId() + ":" + dep.getArtifactId();
            String version = resolve(dep.getVersion(), props);

            if (version == null || version.contains("${")) continue;

            if (scope == null || scope.equals("compile") || scope.equals("runtime")) {
                dependencies.put(ga, version);
            } else {
                devDependencies.put(ga, version);
            }
        }
    }

    private static String resolve(String value, Properties props) {
        if (value == null) return null;
        Matcher m = PROPERTY_REF.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String replacement = props.getProperty(m.group(1));
            m.appendReplacement(sb, replacement != null ? Matcher.quoteReplacement(replacement) : m.group(0));
        }
        m.appendTail(sb);
        return sb.toString();
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
