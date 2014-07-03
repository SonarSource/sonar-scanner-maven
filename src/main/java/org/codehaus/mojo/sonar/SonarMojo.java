package org.codehaus.mojo.sonar;

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

import com.google.common.annotations.VisibleForTesting;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.codehaus.mojo.sonar.bootstrap.InternalMojoBootstraper;
import org.codehaus.mojo.sonar.bootstrap.RunnerBootstraper;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import java.io.IOException;

/**
 * Analyze project. SonarQube server must be started.
 */
@Mojo( name = "sonar",
                aggregator = true,
                requiresDependencyResolution = ResolutionScope.TEST )
public class SonarMojo
    extends AbstractMojo
{

    @Parameter( defaultValue = "${project}", readonly = true )
    protected MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true )
    private MavenSession session;

    /**
     * Sonar host URL.
     */
    @Parameter( property = "sonar.host.url", defaultValue = "http://localhost:9000", alias = "sonar.host.url" )
    private String sonarHostURL;

    /**
     * Set this to 'true' to skip analysis.
     *
     * @since 2.3
     */
    @Parameter( property = "sonar.skip", defaultValue = "false", alias = "sonar.skip" )
    private boolean skip;

    @Component
    protected MavenPluginManager mavenPluginManager;

    @Component
    protected MavenPluginManagerHelper mavenPluginManagerHelper;

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

    @Component( role = org.sonatype.plexus.components.sec.dispatcher.SecDispatcher.class )
    private SecDispatcher securityDispatcher;

    @Component
    private RuntimeInformation runtimeInformation;

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
            ServerMetadata server = new ServerMetadata( sonarHostURL );
            server.logSettings( getLog() );

            String mavenVersion = runtimeInformation.getMavenVersion();

            checkVersionRequirements( server, mavenVersion );

            if ( server.supportsSonarQubeRunnerBootstrappingFromMaven() )
            {
                new RunnerBootstraper( runtimeInformation, getLog(), session, lifecycleExecutor,
                                       artifactFactory, localRepository, artifactMetadataSource, artifactCollector,
                                       dependencyTreeBuilder, projectBuilder, securityDispatcher, server ).execute();
            }
            else
            {
                new InternalMojoBootstraper( server, mavenPluginManager, mavenPluginManagerHelper ).start( project,
                                                                                                           session );
            }

        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Failed to execute SonarQube analysis", e );
        }
    }

    @VisibleForTesting
    void checkVersionRequirements( ServerMetadata server, String mavenVersion )
        throws IOException, MojoExecutionException
    {
        if ( !server.supportsMaven3() )
        {
            throw new MojoExecutionException( "SonarQube " + server.getVersion() + " does not support Maven 3" );
        }
        ArtifactVersion artifactMavenVersion = new DefaultArtifactVersion( mavenVersion );
        if ( ( artifactMavenVersion.getMajorVersion() > 3 || artifactMavenVersion.getMajorVersion() == 3
            && artifactMavenVersion.getMinorVersion() >= 1 )
            && !server.supportsMaven3_1() )
        {
            throw new MojoExecutionException( "SonarQube " + server.getVersion()
                + " does not support Maven 3.1+. Please upgrade to SonarQube 3.7 or greater." );
        }
    }

    @VisibleForTesting
    void setLocalRepository( ArtifactRepository localRepository )
    {
        this.localRepository = localRepository;
    }

    @VisibleForTesting
    void setSonarHostURL( String sonarHostURL )
    {
        this.sonarHostURL = sonarHostURL;
    }

}
