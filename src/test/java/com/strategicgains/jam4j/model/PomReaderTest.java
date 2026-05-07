package com.strategicgains.jam4j.model;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PomReaderTest {

    private File fixture(String name) {
        URL url = getClass().getClassLoader().getResource(name);
        assertThat(url).as("fixture %s not found", name).isNotNull();
        return new File(url.getFile());
    }

    @Test
    void readsProjectMetadata() throws Exception {
        PomReader pom = PomReader.from(fixture("test-pom.xml"));
        assertThat(pom.getName()).isEqualTo("my-app");
        assertThat(pom.getVersion()).isEqualTo("1.2.3");
    }

    @Test
    void mapsCompileAndRuntimeToDependencies() throws Exception {
        PomReader pom = PomReader.from(fixture("test-pom.xml"));
        Map<String, String> deps = pom.getDependencies();
        assertThat(deps).containsEntry("com.fasterxml.jackson.core:jackson-databind", "2.17.1");
        assertThat(deps).containsEntry("org.apache.commons:commons-lang3", "3.14.0");
        assertThat(deps).containsEntry("ch.qos.logback:logback-classic", "1.5.6");
        assertThat(deps).doesNotContainKey("org.junit.jupiter:junit-jupiter");
        assertThat(deps).doesNotContainKey("javax.servlet:javax.servlet-api");
    }

    @Test
    void mapsTestAndProvidedToDevDependencies() throws Exception {
        PomReader pom = PomReader.from(fixture("test-pom.xml"));
        Map<String, String> devDeps = pom.getDevDependencies();
        assertThat(devDeps).containsEntry("org.junit.jupiter:junit-jupiter", "5.11.0");
        assertThat(devDeps).containsEntry("javax.servlet:javax.servlet-api", "4.0.1");
    }

    @Test
    void resolvesPropertyPlaceholdersInVersions() throws Exception {
        PomReader pom = PomReader.from(fixture("test-pom.xml"));
        Map<String, String> deps = pom.getDependencies();
        Map<String, String> devDeps = pom.getDevDependencies();
        assertThat(deps.get("com.fasterxml.jackson.core:jackson-databind")).isEqualTo("2.17.1");
        assertThat(devDeps.get("org.junit.jupiter:junit-jupiter")).isEqualTo("5.11.0");
    }

    @Test
    void allCoordsContainsBothScopes() throws Exception {
        PomReader pom = PomReader.from(fixture("test-pom.xml"));
        List<String> coords = pom.allCoords();
        assertThat(coords).contains(
            "com.fasterxml.jackson.core:jackson-databind:2.17.1",
            "org.apache.commons:commons-lang3:3.14.0",
            "ch.qos.logback:logback-classic:1.5.6",
            "org.junit.jupiter:junit-jupiter:5.11.0",
            "javax.servlet:javax.servlet-api:4.0.1"
        );
    }

    @Test
    void returnsNullMainClassWhenNoPluginConfigured() throws Exception {
        PomReader pom = PomReader.from(fixture("test-pom.xml"));
        assertThat(pom.getMainClass()).isNull();
    }

    @Test
    void extractsMainClassFromMavenJarPlugin() throws Exception {
        PomReader pom = PomReader.from(fixture("test-pom-jar-plugin.xml"));
        assertThat(pom.getMainClass()).isEqualTo("com.example.Main");
    }

    @Test
    void extractsMainClassFromMavenShadePlugin() throws Exception {
        PomReader pom = PomReader.from(fixture("test-pom-shade-plugin.xml"));
        assertThat(pom.getMainClass()).isEqualTo("com.example.ShadeMain");
    }
}
