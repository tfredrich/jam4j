package com.strategicgains.jam4j.command;

import com.strategicgains.jam4j.install.JamUpgrader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "upgrade", mixinStandardHelpOptions = true,
    description = "Upgrade the local jam installation to the latest release.")
public class UpgradeCommand implements Callable<Integer> {

    @Option(names = "--force", description = "Download and install the latest GitHub release even if it is not newer")
    public boolean force;

    @Override
    public Integer call() {
        Path jamHome = Path.of(System.getenv().getOrDefault("JAM_HOME",
            Path.of(System.getProperty("user.home"), ".jam4j").toString()));
        String currentVersion = UpgradeCommand.class.getPackage().getImplementationVersion();

        try {
            return new JamUpgrader().upgrade(jamHome, currentVersion, force) ? 0 : 1;
        } catch (Exception e) {
            System.err.println("jam upgrade: " + e.getMessage());
            return 1;
        }
    }
}
