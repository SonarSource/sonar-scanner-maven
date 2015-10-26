package org.codehaus.mojo.sonar.bootstrap;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.junit.Before;
import org.junit.Test;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonar.runner.api.LogOutput;

import java.util.Properties;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.MapAssert.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RunnerFactoryTest
{
    private LogOutput logOutput;

    private RuntimeInformation runtimeInformation;

    private MavenSession mavenSession;

    private MavenProject rootProject;

    @Before
    public void setUp()
    {
        logOutput = mock( LogOutput.class );
        runtimeInformation = mock( RuntimeInformation.class );
        mavenSession = mock( MavenSession.class );
        rootProject = mock( MavenProject.class );

        Properties system = new Properties();
        system.put( "system", "value" );
        Properties user = new Properties();
        user.put( "user", "value" );
        Properties root = new Properties();
        root.put( "root", "value" );

        when( runtimeInformation.getMavenVersion() ).thenReturn( "1.0" );
        when( mavenSession.getSystemProperties() ).thenReturn( system );
        when( mavenSession.getUserProperties() ).thenReturn( user );
        when( rootProject.getProperties() ).thenReturn( root );
        when( mavenSession.getTopLevelProject() ).thenReturn( rootProject );
    }

    @Test
    public void testProperties()
    {
        RunnerFactory factory = new RunnerFactory( logOutput, false, runtimeInformation, mavenSession );
        EmbeddedRunner runner = factory.create();
        verify( mavenSession ).getUserProperties();
        verify( mavenSession ).getSystemProperties();
        verify( rootProject ).getProperties();

        assertThat( runner.globalProperties() ).includes( entry( "system", "value" ), entry( "user", "value" ),
                                                          entry( "root", "value" ) );
        assertThat( runner.globalProperties() ).includes( entry( "sonar.mojoUseRunner", "true" ) );
    }

    @Test
    public void testDebug()
    {
        RunnerFactory factoryDebug = new RunnerFactory( logOutput, true, runtimeInformation, mavenSession );
        RunnerFactory factory = new RunnerFactory( logOutput, false, runtimeInformation, mavenSession );

        EmbeddedRunner runnerDebug = factoryDebug.create();
        EmbeddedRunner runner = factory.create();

        assertThat( runnerDebug.globalProperties() ).includes( entry( "sonar.verbose", "true" ) );
        assertThat( runner.globalProperties() ).excludes( entry( "sonar.verbose", "true" ) );
    }
}
