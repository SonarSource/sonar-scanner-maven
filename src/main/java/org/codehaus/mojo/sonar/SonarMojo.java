package org.codehaus.mojo.sonar;

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
import org.codehaus.mojo.sonar.bootstrap.LogHandler;

/*
 * The MIT License
 *
 * Copyright 2009 The Codehaus.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import org.codehaus.mojo.sonar.bootstrap.MavenProjectConverter;
import org.codehaus.mojo.sonar.bootstrap.RunnerBootstrapper;
import org.codehaus.mojo.sonar.bootstrap.RunnerFactory;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

/**
 * Analyze project. SonarQube server must be started.
 */
@Mojo( name = "sonar", requiresDependencyResolution = ResolutionScope.TEST, aggregator = true )

public class SonarMojo
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
            DependencyCollector dependencyCollector =
                new DependencyCollector( dependencyTreeBuilder, artifactFactory, localRepository,
                                         artifactMetadataSource, artifactCollector );
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
