package org.codehaus.mojo.sonar;

import org.apache.maven.plugin.logging.Log;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;

import java.util.LinkedList;
import java.util.List;

public class ExtensionsFactory
{
    private final MavenSession session;

    private final LifecycleExecutor lifecycleExecutor;

    private final ArtifactFactory artifactFactory;

    private final ArtifactRepository localRepository;

    private final ArtifactMetadataSource artifactMetadataSource;

    private final ArtifactCollector artifactCollector;

    private final Log log;

    private final DependencyTreeBuilder dependencyTreeBuilder;

    private final MavenProjectBuilder projectBuilder;

    public ExtensionsFactory( Log log, MavenSession session, LifecycleExecutor lifecycleExecutor,
                              ArtifactFactory artifactFactory, ArtifactRepository localRepository,
                              ArtifactMetadataSource artifactMetadataSource, ArtifactCollector artifactCollector,
                              DependencyTreeBuilder dependencyTreeBuilder, MavenProjectBuilder projectBuilder )
    {
        this.log = log;
        this.session = session;
        this.lifecycleExecutor = lifecycleExecutor;
        this.artifactFactory = artifactFactory;
        this.localRepository = localRepository;
        this.artifactMetadataSource = artifactMetadataSource;
        this.artifactCollector = artifactCollector;
        this.dependencyTreeBuilder = dependencyTreeBuilder;
        this.projectBuilder = projectBuilder;
    }

    public List<Object> createExtensionsWithDependencyProperty()
    {
        List<Object> extensions = new LinkedList<>();

        extensions.add( log );
        extensions.add( session );
        extensions.add( lifecycleExecutor );
        extensions.add( projectBuilder );

        return extensions;
    }

    public List<Object> createExtensions()
    {
        List<Object> extensions = createExtensionsWithDependencyProperty();

        extensions.add( artifactFactory );
        extensions.add( localRepository );
        extensions.add( artifactMetadataSource );
        extensions.add( artifactCollector );
        extensions.add( dependencyTreeBuilder );

        return extensions;
    }
}
