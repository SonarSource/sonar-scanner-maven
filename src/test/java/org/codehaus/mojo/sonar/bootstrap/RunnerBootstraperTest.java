package org.codehaus.mojo.sonar.bootstrap;

import static org.mockito.Mockito.when;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.mockito.MockitoAnnotations;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Matchers.any;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonar.runner.api.EmbeddedRunner;
import org.codehaus.mojo.sonar.ExtensionsFactory;
import org.mockito.Mock;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.execution.MavenSession;
import org.junit.Test;

public class RunnerBootstraperTest
{
    @Mock
    private Log log;

    @Mock
    private MavenSession session;

    @Mock
    private SecDispatcher securityDispatcher;

    @Mock
    private EmbeddedRunner runner;

    @Mock
    private MavenProjectConverter mavenProjectConverter;

    @Mock
    private ExtensionsFactory extensionsFactory;

    private RunnerBootstrapper runnerBootstrapper;

    private Properties projectProperties;

    @Before
    public void setUp()
        throws MojoExecutionException
    {
        MockitoAnnotations.initMocks( this );

        projectProperties = new Properties();
        when(
              mavenProjectConverter.configure( anyListOf( MavenProject.class ), any( MavenProject.class ),
                                               any( Properties.class ) ) ).thenReturn( projectProperties );
        List<Object> extensions = new LinkedList<Object>();
        extensions.add( new Object() );
        when( extensionsFactory.createExtensions() ).thenReturn( extensions );
        when( extensionsFactory.createExtensionsWithDependencyProperty() ).thenReturn( extensions );

        when( runner.mask( anyString() ) ).thenReturn( runner );
        when( runner.unmask( anyString() ) ).thenReturn( runner );
        runnerBootstrapper =
            new RunnerBootstrapper( log, session, securityDispatcher, runner, mavenProjectConverter, extensionsFactory );
    }

    @Test
    public void testSQ52()
        throws MojoExecutionException, IOException
    {
        when( runner.serverVersion() ).thenReturn( "5.2" );
        runnerBootstrapper.execute();

        verifyCommonCalls();

        // no extensions, mask or unmask
        verifyNoMoreInteractions( runner );
    }

    @Test
    public void testSQ51()
        throws MojoExecutionException, IOException
    {
        when( runner.serverVersion() ).thenReturn( "5.1" );
        runnerBootstrapper.execute();

        verifyCommonCalls();

        verify( extensionsFactory ).createExtensionsWithDependencyProperty();
        verify( runner ).addExtensions( anyObject() );
        verifyNoMoreInteractions( runner );
    }

    @Test
    public void testSQ60()
        throws MojoExecutionException, IOException
    {
        when( runner.serverVersion() ).thenReturn( "6.0" );
        runnerBootstrapper.execute();

        verifyCommonCalls();

        // no extensions, mask or unmask
        verifyNoMoreInteractions( runner );
    }

    @Test
    public void testSQ48()
        throws MojoExecutionException, IOException
    {
        when( runner.serverVersion() ).thenReturn( "4.8" );
        runnerBootstrapper.execute();

        verifyCommonCalls();

        verify( extensionsFactory ).createExtensions();
        verify( runner ).addExtensions( any( Object[].class ) );
        verifyNoMoreInteractions( runner );
    }

    private void verifyCommonCalls()
    {
        verify( runner, atLeastOnce() ).mask( anyString() );
        verify( runner, atLeastOnce() ).unmask( anyString() );
        
        verify( runner ).serverVersion();
        verify( runner ).start();
        verify( runner ).stop();
        verify( runner ).runAnalysis( projectProperties );
    }
}
