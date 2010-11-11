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

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.repository.DefaultRepositoryRequest;
import org.apache.maven.artifact.repository.RepositoryRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.repository.RemoteRepository;

/**
 * Configure pom and execute sonar internal maven plugin
 */
public class Bootstraper
{

    private ServerMetadata server;
    private MavenPluginManager pluginManager;

    public Bootstraper( ServerMetadata server, MavenPluginManager pluginManager )
    {
        this.server = server;
        this.pluginManager = pluginManager;
    }

    public void start( MavenProject project, MavenSession session ) throws IOException, MojoExecutionException
    {
        executeMojo( project, session );
    }

    private void executeMojo( MavenProject project, MavenSession session ) throws MojoExecutionException
    {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            
            RepositoryRequest repositoryRequest = new DefaultRepositoryRequest();
            repositoryRequest.setLocalRepository( session.getLocalRepository() );
            repositoryRequest.setRemoteRepositories( project.getPluginArtifactRepositories() );

            Plugin plugin = createSonarPlugin();

            List<RemoteRepository> remoteRepositories = session.getCurrentProject().getRemotePluginRepositories();
            
            PluginDescriptor pluginDescriptor = pluginManager.getPluginDescriptor( plugin, remoteRepositories ,
                session.getRepositorySession() );

            String goal = "sonar";

            MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo( goal );
            if ( mojoDescriptor == null )
            {
                throw new MojoExecutionException( "Unknown mojo goal: " + goal );
            }
            MojoExecution mojoExecution = new MojoExecution( plugin, goal, "sonar" + goal );

            mojoExecution.setConfiguration( convert( mojoDescriptor ) );

            mojoExecution.setMojoDescriptor( mojoDescriptor );

            // olamy : we exclude nothing and import nothing regarding realm import and artifacts
            
            DependencyFilter artifactFilter = new DependencyFilter()
            {
                public boolean accept( DependencyNode arg0, List<DependencyNode> arg1 )
                {
                    return true;
                }
            };
            
            pluginManager.setupPluginRealm( pluginDescriptor, session, Thread.currentThread().getContextClassLoader(),
                Collections.<String>emptyList(), artifactFilter );            

            Mojo mojo = pluginManager.getConfiguredMojo( Mojo.class, session, mojoExecution );
            Thread.currentThread().setContextClassLoader( pluginDescriptor.getClassRealm() );
            mojo.execute();

        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Can not execute Sonar", e );
        }
        finally
        {
            Thread.currentThread().setContextClassLoader( originalClassLoader );
        }
    }

    private Xpp3Dom convert( MojoDescriptor mojoDescriptor )
    {
        Xpp3Dom dom = new Xpp3Dom( "configuration" );

        PlexusConfiguration c = mojoDescriptor.getMojoConfiguration();

        PlexusConfiguration[] ces = c.getChildren();

        if ( ces != null )
        {
            for ( PlexusConfiguration ce : ces )
            {
                String value = ce.getValue( null );
                String defaultValue = ce.getAttribute( "default-value", null );
                if ( value != null || defaultValue != null )
                {
                    Xpp3Dom e = new Xpp3Dom( ce.getName() );
                    e.setValue( value );
                    if ( defaultValue != null )
                    {
                        e.setAttribute( "default-value", defaultValue );
                    }
                    dom.addChild( e );
                }
            }
        }

        return dom;
    }

    private Plugin createSonarPlugin() throws IOException
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId( "org.codehaus.sonar" );
        plugin.setArtifactId( "sonar-maven3-plugin" );
        plugin.setVersion( server.getVersion() );
        return plugin;
    }
}
