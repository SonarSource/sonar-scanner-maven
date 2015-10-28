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

import org.codehaus.mojo.sonar.ExtensionsFactory;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import java.io.IOException;
import java.util.Properties;

/**
 * Configure properties and bootstrap using SonarQube runner API (need SQ 4.3+)
 */
public class RunnerBootstrapper
{

    private final Log log;

    private final MavenSession session;

    private final SecDispatcher securityDispatcher;

    private final EmbeddedRunner runner;

    private final MavenProjectConverter mavenProjectConverter;

    private final ExtensionsFactory extensionsFactory;

    private String serverVersion;

    public RunnerBootstrapper( Log log, MavenSession session, SecDispatcher securityDispatcher, EmbeddedRunner runner,
                               MavenProjectConverter mavenProjectConverter, ExtensionsFactory extensionsFactory )
    {
        this.log = log;
        this.session = session;
        this.securityDispatcher = securityDispatcher;
        this.runner = runner;
        this.mavenProjectConverter = mavenProjectConverter;
        this.extensionsFactory = extensionsFactory;
    }

    public void execute()
        throws IOException, MojoExecutionException
    {
        try
        {
            applyMasks();
            runner.start();
            serverVersion = runner.serverVersion();
            log.info( "SonarQube version: " + serverVersion );

            if ( this.isVersionPriorTo5Dot2() )
            {
                // for these versions, global properties and extensions are only applied when calling runAnalisys()
                if ( this.supportsNewDependencyProperty() )
                {
                    runner.addExtensions( extensionsFactory.createExtensionsWithDependencyProperty().toArray() );
                }
                else
                {
                    runner.addExtensions( extensionsFactory.createExtensions().toArray() );
                }

            }
            if ( log.isDebugEnabled() )
            {
                runner.setGlobalProperty( "sonar.verbose", "true" );
            }

            runner.runAnalysis( collectProperties() );
            runner.stop();
        }
        catch ( Exception e )
        {
            throw ExceptionHandling.handle( e, log );
        }
    }

    private void applyMasks()
    {
        // Exclude log implementation to not conflict with Maven 3.1 logging impl
        runner.mask( "org.slf4j.LoggerFactory" );
        // Include slf4j Logger that is exposed by some Sonar components
        runner.unmask( "org.slf4j.Logger" );
        runner.unmask( "org.slf4j.ILoggerFactory" );
        // MSONAR-122
        runner.unmask( "org.slf4j.Marker" );
        // Exclude other slf4j classes
        // .unmask("org.slf4j.impl.")
        runner.mask( "org.slf4j." );
        // Exclude logback
        runner.mask( "ch.qos.logback." );
        runner.mask( "org.sonar." );
        // Guava is not the same version in SonarQube classloader
        runner.mask( "com.google.common" );
        // Include everything else (we need to unmask all extensions that might be passed to the batch)
        runner.unmask( "" );
    }

    private void checkDumpToFile( Properties props )
    {
        String dumpToFile = props.getProperty( "sonarRunner.dumpToFile" );
        if ( dumpToFile != null )
        {
            runner.setGlobalProperty( "sonarRunner.dumpToFile", dumpToFile );
        }
    }

    private Properties collectProperties()
        throws MojoExecutionException
    {
        Properties props =
            mavenProjectConverter.configure( session.getProjects(), session.getTopLevelProject(),
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

    public boolean supportsNewDependencyProperty()
    {
        return !isVersionPriorTo5Dot0();
    }

    public boolean isVersionPriorTo5Dot2()
    {
        if ( serverVersion == null )
        {
            return true;
        }
        ArtifactVersion artifactVersion = new DefaultArtifactVersion( serverVersion );
        if ( artifactVersion.getMajorVersion() < 5 )
        {
            return true;
        }

        return artifactVersion.getMajorVersion() == 5 && artifactVersion.getMinorVersion() < 2;
    }

    public boolean isVersionPriorTo5Dot0()
    {
        if ( serverVersion == null )
        {
            return true;
        }
        ArtifactVersion artifactVersion = new DefaultArtifactVersion( serverVersion );
        return artifactVersion.getMajorVersion() < 5;
    }
}
