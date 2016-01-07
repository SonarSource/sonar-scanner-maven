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
package org.sonarsource.scanner.maven.bootstrap;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonarsource.scanner.maven.ExtensionsFactory;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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

        MavenProject rootProject = mock( MavenProject.class );
        when( rootProject.isExecutionRoot() ).thenReturn( true );
        when( session.getSortedProjects() ).thenReturn( Arrays.asList( rootProject ) );

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
            new RunnerBootstrapper( log, session, securityDispatcher, runner, mavenProjectConverter,
                                    extensionsFactory );
    }

    @Test
    public void testSQ52()
        throws MojoExecutionException, IOException
    {
        when( runner.serverVersion() ).thenReturn( "5.2" );
        runnerBootstrapper.execute();

        Assertions.assertThat( runnerBootstrapper.isVersionPriorTo4Dot5() ).isFalse();

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

        Assertions.assertThat( runnerBootstrapper.isVersionPriorTo4Dot5() ).isFalse();

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

        Assertions.assertThat( runnerBootstrapper.isVersionPriorTo4Dot5() ).isFalse();

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

        Assertions.assertThat( runnerBootstrapper.isVersionPriorTo4Dot5() ).isFalse();

        verifyCommonCalls();

        verify( extensionsFactory ).createExtensions();
        verify( runner ).addExtensions( any( Object[].class ) );
        verifyNoMoreInteractions( runner );
    }

    @Test
    public void testSQ44()
        throws MojoExecutionException, IOException
    {
        when( runner.serverVersion() ).thenReturn( "4.4" );
        runnerBootstrapper.execute();

        Assertions.assertThat( runnerBootstrapper.isVersionPriorTo4Dot5() ).isTrue();

        verifyCommonCalls();

        verify( extensionsFactory ).createExtensions();
        verify( runner ).addExtensions( any( Object[].class ) );
        verifyNoMoreInteractions( runner );
    }

    @Test
    public void testNullServerVersion()
        throws MojoExecutionException, IOException
    {
        when( runner.serverVersion() ).thenReturn( null );
        Assertions.assertThat( runnerBootstrapper.isVersionPriorTo5Dot0() ).isTrue();
        Assertions.assertThat( runnerBootstrapper.isVersionPriorTo5Dot2() ).isTrue();
        Assertions.assertThat( runnerBootstrapper.isVersionPriorTo4Dot5() ).isTrue();

        runnerBootstrapper.execute();
        verify( log ).warn( contains( "it is recommended to use maven-sonar-plugin 2.6" ) );
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