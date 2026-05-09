package com.strategicgains.jam4j.resolver;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.nio.file.Path;

public class ResolverFactory {

    static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    static final int REQUEST_TIMEOUT_MILLIS = 30_000;
    static final int HTTP_RETRY_COUNT = 1;
    static final long HTTP_RETRY_INTERVAL_MILLIS = 1_000L;

    public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                throw new RuntimeException("Failed to initialize Maven Resolver service: " + type.getName(), exception);
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    public static DefaultRepositorySystemSession newSession(RepositorySystem system, Path localRepoPath) {
        return newSession(system, localRepoPath, false, null);
    }

    public static DefaultRepositorySystemSession newSession(
            RepositorySystem system,
            Path localRepoPath,
            boolean ignoreArtifactDescriptorRepositories,
            TransferListener transferListener) {

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        // Model builder uses system properties to evaluate JDK profile activation (java.version, etc.)
        session.setSystemProperties(System.getProperties());
        session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT_MILLIS);
        session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, REQUEST_TIMEOUT_MILLIS);
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, HTTP_RETRY_COUNT);
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL, HTTP_RETRY_INTERVAL_MILLIS);
        session.setConfigProperty(ConfigurationProperties.USER_AGENT, "jam4j");
        session.setIgnoreArtifactDescriptorRepositories(ignoreArtifactDescriptorRepositories);
        if (transferListener != null) {
            session.setTransferListener(transferListener);
        }
        LocalRepository localRepo = new LocalRepository(localRepoPath.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }
}
