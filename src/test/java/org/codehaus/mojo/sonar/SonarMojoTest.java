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

import org.junit.Before;
import org.apache.maven.plugin.logging.Log;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.testing.MojoRule;
import org.fest.assertions.MapAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.MapAssert.entry;

public class SonarMojoTest
{
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();

    private Log mockedLogger;

    private SonarMojo getMojo( File baseDir )
        throws Exception
    {
        return (SonarMojo) mojoRule.lookupConfiguredMojo( baseDir, "sonar" );
    }

    @Before
    public void setUpMocks()
    {
        mockedLogger = mock( Log.class );
    }

    @Test
    public void executeMojo()
        throws Exception
    {
        File baseDir = executeProject( "sample-project", temp.newFile() );
        
        // passed in the properties of the profile and project
        assertGlobalPropsContains( entry( "sonar.host.url1", "http://myserver:9000" ) );
        assertGlobalPropsContains( entry( "sonar.host.url2", "http://myserver:9000" ) );
    }

    @Test
    public void shouldExportBinaries()
        throws Exception
    {
        File localRepo = temp.newFolder();
        final ArtifactRepository localRepository =
            new DefaultArtifactRepository( "local", localRepo.toURI().toURL().toString(), new DefaultRepositoryLayout() );
        File commonsIo = new File( localRepo, "commons-io/commons-io/2.4/commons-io-2.4.jar" );
        FileUtils.forceMkdir( commonsIo.getParentFile() );
        commonsIo.createNewFile();

        File baseDir = new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/sample-project" );
        SonarMojo mojo = getMojo( baseDir );
        mojo.setLocalRepository( localRepository );
        mojo.execute();

        assertPropsContains( entry( "sonar.binaries", new File( baseDir, "target/classes" ).getAbsolutePath() ) );
    }

    @Test
    public void shouldExportDefaultWarWebSource()
        throws Exception
    {
        File baseDir = executeProject( "sample-war-project", temp.newFile() );
        assertPropsContains( entry( "sonar.sources",
                                    new File( baseDir, "src/main/webapp" ).getAbsolutePath() + ","
                                        + new File( baseDir, "pom.xml" ).getAbsolutePath() + ","
                                        + new File( baseDir, "src/main/java" ).getAbsolutePath() ) );
    }

    @Test
    public void shouldExportOverridenWarWebSource()
        throws Exception
    {
        File baseDir = executeProject( "war-project-override-web-dir", temp.newFile() );
        assertPropsContains( entry( "sonar.sources",
                                    new File( baseDir, "web" ).getAbsolutePath() + ","
                                        + new File( baseDir, "pom.xml" ).getAbsolutePath() + ","
                                        + new File( baseDir, "src/main/java" ).getAbsolutePath() ) );
    }

    @Test
    public void shouldExportDependencies()
        throws Exception
    {
        File localRepo = new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/repository" );
        final ArtifactRepository localRepository =
            new DefaultArtifactRepository( "local", localRepo.toURI().toURL().toString(), new DefaultRepositoryLayout() );
        File baseDir = executeProject( "export-dependencies", localRepo );

        Properties outProps = readProps( "target/dump.properties" );
        String libJson = outProps.getProperty( "sonar.maven.projectDependencies" );

        JSONAssert.assertEquals( "[{\"k\":\"commons-io:commons-io\",\"v\":\"2.4\",\"s\":\"compile\",\"d\":["
            + "{\"k\":\"commons-lang:commons-lang\",\"v\":\"2.6\",\"s\":\"compile\",\"d\":[]}" + "]},"
            + "{\"k\":\"junit:junit\",\"v\":\"3.8.1\",\"s\":\"test\",\"d\":[]}]", libJson, true );

        assertThat( outProps.getProperty( "sonar.java.binaries" ) ).isEqualTo( new File( baseDir, "target/classes" ).getAbsolutePath() );
        assertThat( outProps.getProperty( "sonar.java.test.binaries" ) ).isEqualTo( new File( baseDir,
                                                                                              "target/test-classes" ).getAbsolutePath() );
    }

    // MSONAR-113
    @Test
    public void shouldExportSurefireReportsPath()
        throws Exception
    {

        File baseDir = executeProject( "sample-project-with-surefire", temp.newFile() );
        assertPropsContains( entry( "sonar.junit.reportsPath",
                                    new File( baseDir, "target/surefire-reports" ).getAbsolutePath() ) );
    }

    // MSONAR-113
    @Test
    public void shouldExportSurefireCustomReportsPath()
        throws Exception
    {
        File baseDir = executeProject( "sample-project-with-custom-surefire-path", temp.newFile() );
        assertPropsContains( entry( "sonar.junit.reportsPath", new File( baseDir, "target/tests" ).getAbsolutePath() ) );
    }

    @Test
    public void findbugsExcludeFile()
        throws IOException, Exception
    {
        executeProject( "project-with-findbugs", temp.newFile() );
        assertPropsContains( entry( "sonar.findbugs.excludeFilters", "findbugs-exclude.xml" ) );
        assertThat( readProps( "target/dump.properties.global" ) ).excludes( ( entry( "sonar.verbose", "true" ) ) );

    }

    @Test
    public void verbose()
        throws Exception
    {
        when( mockedLogger.isDebugEnabled() ).thenReturn( true );
        executeProject( "project-with-findbugs", temp.newFile() );
        verify( mockedLogger, atLeastOnce() ).isDebugEnabled();
        assertThat( readProps( "target/dump.properties.global" ) ).includes( ( entry( "sonar.verbose", "true" ) ) );
    }

    private File executeProject( String projectName, File localRepo )
        throws Exception
    {
        ArtifactRepository artifactRepo =
            new DefaultArtifactRepository( "local", localRepo.toURI().toURL().toString(), new DefaultRepositoryLayout() );

        File baseDir = new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/" + projectName );
        SonarMojo mojo = getMojo( baseDir );
        mojo.setLocalRepository( artifactRepo );
        mojo.setLog( mockedLogger );

        mojo.execute();

        return baseDir;
    }

    private void assertPropsContains( MapAssert.Entry... entries )
        throws FileNotFoundException, IOException
    {
        assertThat( readProps( "target/dump.properties" ) ).includes( entries );
    }

    private void assertGlobalPropsContains( MapAssert.Entry... entries )
        throws FileNotFoundException, IOException
    {
        assertThat( readProps( "target/dump.properties.global" ) ).includes( entries );
    }

    private Properties readProps( String filePath )
        throws FileNotFoundException, IOException
    {
        FileInputStream fis = null;
        try
        {
            File dump = new File( filePath );
            Properties props = new Properties();
            fis = new FileInputStream( dump );
            props.load( fis );
            return props;
        }
        finally
        {
            IOUtils.closeQuietly( fis );
        }
    }

}
