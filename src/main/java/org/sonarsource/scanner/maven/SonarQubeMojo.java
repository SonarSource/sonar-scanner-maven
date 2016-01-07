/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.scanner.maven;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonarsource.scanner.maven.bootstrap.LogHandler;
import org.sonarsource.scanner.maven.bootstrap.MavenProjectConverter;
import org.sonarsource.scanner.maven.bootstrap.RunnerBootstrapper;
import org.sonarsource.scanner.maven.bootstrap.RunnerFactory;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

/**
 * Analyze project. SonarQube server must be started.
 */
@Mojo( name = "sonar", requiresDependencyResolution = ResolutionScope.TEST, aggregator = true )

public class SonarQubeMojo
    extends AbstractMojo
{

    @Parameter( defaultValue = "${session}", readonly = true )
    private MavenSession session;

    /**
     * Set this to 'true' to skip analysis.
     *
     * @since 2.3
     */
    @Parameter( property = "sonar.skip", defaultValue = "false", alias = "sonar.skip" )
    private boolean skip;

    @Component
    private LifecycleExecutor lifecycleExecutor;

    @Component
    private ArtifactFactory artifactFactory;

    @Parameter( defaultValue = "${localRepository}", readonly = true, required = true )
    private ArtifactRepository localRepository;

    @Component
    private ArtifactMetadataSource artifactMetadataSource;

    @Component
    private ArtifactCollector artifactCollector;

    @Component
    private DependencyTreeBuilder dependencyTreeBuilder;

    @Component
    private MavenProjectBuilder projectBuilder;

    @Component( hint = "mng-4384" )
    private SecDispatcher securityDispatcher;

    @Component
    private RuntimeInformation runtimeInformation;

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skip )
        {
            getLog().info( "sonar.skip = true: Skipping analysis" );
            return;
        }
        try
        {
            ExtensionsFactory extensionsFactory =
                new ExtensionsFactory( getLog(), session, lifecycleExecutor, artifactFactory, localRepository,
                                       artifactMetadataSource, artifactCollector, dependencyTreeBuilder,
                                       projectBuilder );
            DependencyCollector dependencyCollector = new DependencyCollector( dependencyTreeBuilder, localRepository );
            MavenProjectConverter mavenProjectConverter = new MavenProjectConverter( getLog(), dependencyCollector );
            LogHandler logHandler = new LogHandler( getLog() );
            RunnerFactory runnerFactory =
                new RunnerFactory( logHandler, getLog().isDebugEnabled(), runtimeInformation, session );

            EmbeddedRunner runner = runnerFactory.create();

            new RunnerBootstrapper( getLog(), session, securityDispatcher, runner, mavenProjectConverter,
                                    extensionsFactory ).execute();
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Failed to execute SonarQube analysis", e );
        }
    }

    @VisibleForTesting
    void setLocalRepository( ArtifactRepository localRepository )
    {
        this.localRepository = localRepository;
    }

    @VisibleForTesting
    MavenSession getSession()
    {
        return session;
    }
}
