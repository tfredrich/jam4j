package com.strategicgains.jam4j.model;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;

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

            if (version == null) continue;
            if (version.contains("${")) {
                System.err.println("Warning: skipping " + ga + " — unresolved version: " + version);
                continue;
            }

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

    public String getMainClass() {
        if (model.getBuild() == null) return null;
        for (Plugin plugin : model.getBuild().getPlugins()) {
            String mainClass = mainClassFromPlugin(plugin);
            if (mainClass != null) return mainClass;
        }
        return null;
    }

    private static String mainClassFromPlugin(Plugin plugin) {
        String artifactId = plugin.getArtifactId();
        if ("maven-jar-plugin".equals(artifactId)) {
            return xpathValue((Xpp3Dom) plugin.getConfiguration(), "archive", "manifest", "mainClass");
        }
        if ("maven-shade-plugin".equals(artifactId)) {
            for (PluginExecution execution : plugin.getExecutions()) {
                String v = shadeMainClass((Xpp3Dom) execution.getConfiguration());
                if (v != null) return v;
            }
            return shadeMainClass((Xpp3Dom) plugin.getConfiguration());
        }
        return null;
    }

    private static String shadeMainClass(Xpp3Dom config) {
        if (config == null) return null;
        Xpp3Dom transformers = config.getChild("transformers");
        if (transformers == null) return null;
        for (Xpp3Dom transformer : transformers.getChildren("transformer")) {
            Xpp3Dom child = transformer.getChild("mainClass");
            if (child != null && child.getValue() != null) return child.getValue().trim();
        }
        return null;
    }

    private static String xpathValue(Xpp3Dom node, String... path) {
        for (String segment : path) {
            if (node == null) return null;
            node = node.getChild(segment);
        }
        return node != null ? node.getValue() : null;
    }
}
