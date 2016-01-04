package org.codehaus.mojo.sonar.bootstrap;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.sonar.ExtensionsFactory;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

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

            checkSQVersion();

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

    private Properties collectProperties()
        throws MojoExecutionException
    {
        List<MavenProject> sortedProjects = session.getSortedProjects();
        MavenProject topLevelProject = null;
        for ( MavenProject project : sortedProjects )
        {
            if ( project.isExecutionRoot() )
            {
                topLevelProject = project;
                break;
            }
        }
        if ( topLevelProject == null )
        {
            throw new IllegalStateException( "Maven session does not declare a top level project" );
        }
        Properties props =
            mavenProjectConverter.configure( sortedProjects, topLevelProject, session.getUserProperties() );
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

    public void checkSQVersion()
    {
        if ( serverVersion != null )
        {
            log.info( "SonarQube version: " + serverVersion );
        }

        if ( isVersionPriorTo4Dot5() )
        {
            log.warn( "With SonarQube prior to 4.5, it is recommended to use maven-sonar-plugin 2.6" );
        }
    }

    public boolean isVersionPriorTo4Dot5()
    {
        if ( serverVersion == null )
        {
            return true;
        }
        ArtifactVersion artifactVersion = new DefaultArtifactVersion( serverVersion );
        if ( artifactVersion.getMajorVersion() < 4 )
        {
            return true;
        }

        return artifactVersion.getMajorVersion() == 4 && artifactVersion.getMinorVersion() < 5;
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
