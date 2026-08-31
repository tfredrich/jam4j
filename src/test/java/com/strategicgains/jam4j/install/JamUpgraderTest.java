package com.strategicgains.jam4j.install;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JamUpgraderTest {

    @Test
    void comparesReleaseVersionsSemantically() {
        assertThat(JamUpgrader.SemanticVersion.compare("1.9.0", "1.10.0")).isNegative();
        assertThat(JamUpgrader.SemanticVersion.compare("v1.0.0", "1.0.0")).isZero();
        assertThat(JamUpgrader.SemanticVersion.compare("1.0.1-SNAPSHOT", "1.0.1")).isNegative();
        assertThat(JamUpgrader.SemanticVersion.compare("1.0.0", "1.0.1")).isNegative();
    }

    @Test
    void rejectsMalformedVersions() {
        assertThatThrownBy(() -> JamUpgrader.SemanticVersion.compare("dev", "1.0.0"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
