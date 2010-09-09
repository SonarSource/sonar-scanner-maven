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
package org.codehaus.mojo.sonar;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Configure pom and execute sonar internal maven plugin
 */
public class Bootstraper
{

    public static final String REPOSITORY_ID = "sonar";

    private ServerMetadata server;

    private PluginManager pluginManager;

    private ArtifactRepositoryFactory repoFactory;

    private Log log;

    public Bootstraper( ServerMetadata server, ArtifactRepositoryFactory repoFactory, PluginManager pluginManager,
                        Log log )
    {
        this.server = server;
        this.repoFactory = repoFactory;
        this.pluginManager = pluginManager;
        this.log = log;
    }

    public void start( MavenProject project, MavenSession session )
        throws IOException, MojoExecutionException
    {
        if ( server.needsSonarInternalRepository() )
        {
            configureInternalRepositories( project );
            executeMojo( project, session, createDeprecatedPlugin(), "internal" );
        }
        else
        {
            executeMojo( project, session, createPlugin(), "sonar" );
        }
    }

    private void executeMojo( MavenProject project, MavenSession session, Plugin plugin, String goal )
        throws MojoExecutionException
    {
        try
        {
            log.info(
                "Execute: " + plugin.getGroupId() + ":" + plugin.getArtifactId() + ":" + plugin.getVersion() + ":" +
                    goal );
            PluginDescriptor pluginDescriptor =
                pluginManager.verifyPlugin( plugin, project, session.getSettings(), session.getLocalRepository() );
            MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo( goal );
            if ( mojoDescriptor == null )
            {
                throw new MojoExecutionException( "Unknown mojo goal: " + goal );
            }
            pluginManager.executeMojo( project, new MojoExecution( mojoDescriptor ), session );

        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Can not execute Sonar", e );
        }
    }

    private void configureInternalRepositories( MavenProject project )
        throws IOException
    {
        List<ArtifactRepository> pluginRepositories = new ArrayList<ArtifactRepository>();
        ArtifactRepository repository = createSonarRepository( repoFactory );
        pluginRepositories.add( repository );
        pluginRepositories.addAll( project.getPluginArtifactRepositories() );
        project.setPluginArtifactRepositories( pluginRepositories );

        List<ArtifactRepository> artifactRepositories = new ArrayList<ArtifactRepository>();
        artifactRepositories.add( repository );
        artifactRepositories.addAll( project.getRemoteArtifactRepositories() );
        project.setRemoteArtifactRepositories( artifactRepositories );

    }

    private ArtifactRepository createSonarRepository( ArtifactRepositoryFactory repoFactory )
    {
        return repoFactory.createArtifactRepository( REPOSITORY_ID, server.getMavenRepositoryUrl(),
                                                     new DefaultRepositoryLayout(),
                                                     new ArtifactRepositoryPolicy( false, "never", "ignore" ),
                                                     // snapshot
                                                     new ArtifactRepositoryPolicy( true, "never", "ignore" ) );
    }

    private Plugin createDeprecatedPlugin()
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId( "org.codehaus.sonar.runtime" );
        plugin.setArtifactId( "sonar-core-maven-plugin" );
        plugin.setVersion( server.getKey() );
        return plugin;
    }

    private Plugin createPlugin()
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId( "org.codehaus.sonar" );
        plugin.setArtifactId( "sonar-maven-plugin" );
        plugin.setVersion( server.getVersion() );
        return plugin;
    }
}
