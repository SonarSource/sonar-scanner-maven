package org.codehaus.mojo.sonar.bootstrap;

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

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.codehaus.mojo.sonar.ServerMetadata;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import java.io.IOException;
import java.util.Properties;

/**
 * Configure properties and bootstrap using SonarQube runner API (need SQ 4.3+)
 */
public class RunnerBootstraper
{

    private final RuntimeInformation runtimeInformation;

    private final Log log;

    private final MavenSession session;

    private final LifecycleExecutor lifecycleExecutor;

    private final ArtifactFactory artifactFactory;

    private final ArtifactRepository localRepository;

    private final ArtifactMetadataSource artifactMetadataSource;

    private final ArtifactCollector artifactCollector;

    private final DependencyTreeBuilder dependencyTreeBuilder;

    private final MavenProjectBuilder projectBuilder;

    private final SecDispatcher securityDispatcher;

    private ServerMetadata server;

    public RunnerBootstraper( RuntimeInformation runtimeInformation, Log log,
                              MavenSession session, LifecycleExecutor lifecycleExecutor,
                              ArtifactFactory artifactFactory, ArtifactRepository localRepository,
                              ArtifactMetadataSource artifactMetadataSource, ArtifactCollector artifactCollector,
                              DependencyTreeBuilder dependencyTreeBuilder, MavenProjectBuilder projectBuilder,
                              SecDispatcher securityDispatcher, ServerMetadata server )
    {
        this.runtimeInformation = runtimeInformation;
        this.log = log;
        this.session = session;
        this.lifecycleExecutor = lifecycleExecutor;
        this.artifactFactory = artifactFactory;
        this.localRepository = localRepository;
        this.artifactMetadataSource = artifactMetadataSource;
        this.artifactCollector = artifactCollector;
        this.dependencyTreeBuilder = dependencyTreeBuilder;
        this.projectBuilder = projectBuilder;
        this.securityDispatcher = securityDispatcher;
        this.server = server;
    }

    public void execute()
        throws IOException, MojoExecutionException
    {
        try
        {
            EmbeddedRunner runner =
                EmbeddedRunner.create()
                              .setApp( "Maven", runtimeInformation.getMavenVersion() )
                              .addProperties( session.getSystemProperties() );
            // Exclude log implementation to not conflict with Maven 3.1 logging impl
            runner.mask( "org.slf4j.LoggerFactory" )
                  // Include slf4j Logger that is exposed by some Sonar components
                  .unmask( "org.slf4j.Logger" ).unmask( "org.slf4j.ILoggerFactory" )
                  // Exclude other slf4j classes
                  // .unmask("org.slf4j.impl.")
                  .mask( "org.slf4j." )
                  // Exclude logback
                  .mask( "ch.qos.logback." )
                  .mask( "org.sonar." )
                  // Guava is not the same version in SonarQube classloader
                  .mask( "com.google.common" )
                  // Include everything else
                  .unmask( "" );
            runner.addExtensions( session, log, lifecycleExecutor, projectBuilder );
            if ( !server.supportsNewDependencyProperty() )
            {
                runner.addExtensions( artifactFactory, localRepository, artifactMetadataSource, artifactCollector,
                                      dependencyTreeBuilder );
            }
            if ( log.isDebugEnabled() )
            {
                runner.setProperty( "sonar.verbose", "true" );
            }
            runner.addProperties( collectProperties() );

            // Secret property to manage backward compatibility on SQ side (see ProjectScanContainer)
            runner.setProperty( "sonar.mojoUseRunner", "true" );

            runner.execute();
        }
        catch ( Exception e )
        {
            throw ExceptionHandling.handle( e, log );
        }
    }

    private Properties collectProperties()
        throws MojoExecutionException
    {
        Properties props =
            new MavenProjectConverter( log, server.supportsFilesAsSources(),
                                       new DependencyCollector( dependencyTreeBuilder, artifactFactory,
                                                                localRepository, artifactMetadataSource,
                                                                artifactCollector ) ).configure( session.getProjects(),
                                                                                                 session.getTopLevelProject(),
                                                                                                 session.getUserProperties() );
        props.putAll( decryptProperties( props ) );

        return props;
    }

    public Properties decryptProperties( Properties properties )
    {
        Properties newProperties = new Properties();
        try
        {
            for ( String key : properties.stringPropertyNames() )
            {
                if ( key.contains( ".password" ) )
                {
                    decrypt( properties, newProperties, key );
                }
            }
        }
        catch ( Exception e )
        {
            log.warn( "Unable to decrypt properties", e );
        }
        return newProperties;
    }

    private void decrypt( Properties properties, Properties newProperties, String key )
    {
        try
        {
            String decrypted = securityDispatcher.decrypt( properties.getProperty( key ) );
            newProperties.setProperty( key, decrypted );
        }
        catch ( SecDispatcherException e )
        {
            log.debug( "Unable to decrypt property " + key, e );
        }
    }
}
