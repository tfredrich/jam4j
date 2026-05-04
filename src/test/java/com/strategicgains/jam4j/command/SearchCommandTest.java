package com.strategicgains.jam4j.command;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCommandTest {

    @Test
    void shortInteractiveFlagMatchesLongFlag() {
        SearchCommand shortFlag = parse("-i", "HyperExpress");
        SearchCommand longFlag = parse("--interactive", "HyperExpress");

        assertThat(shortFlag.interactive).isTrue();
        assertThat(longFlag.interactive).isTrue();
        assertThat(shortFlag.queryParts).containsExactlyElementsOf(longFlag.queryParts);
    }

    private SearchCommand parse(String... args) {
        SearchCommand command = new SearchCommand();
        new CommandLine(command).parseArgs(args);
        return command;
    }
}
