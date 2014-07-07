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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;

import static org.fest.assertions.Assertions.assertThat;

public class MavenProjectConverterTest
{

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void convertSingleModuleProject()
        throws Exception
    {
        File baseDir = temp.newFolder();
        MavenProject project = new MavenProject();
        project.getModel().setGroupId( "com.foo" );
        project.getModel().setArtifactId( "myProject" );
        project.getModel().setName( "My Project" );
        project.getModel().setDescription( "My sample project" );
        project.getModel().setVersion( "2.1" );
        project.setFile( new File( baseDir, "pom.xml" ) );
        Properties props =
            new MavenProjectConverter( false ).configure( Arrays.asList( project ), project, new Properties() );
        assertThat( props.getProperty( "sonar.projectKey" ) ).isEqualTo( "com.foo:myProject" );
        assertThat( props.getProperty( "sonar.projectName" ) ).isEqualTo( "My Project" );
        assertThat( props.getProperty( "sonar.projectVersion" ) ).isEqualTo( "2.1" );
    }

    @Test
    public void shouldIncludePomIfRequested()
        throws Exception
    {
        File baseDir = temp.newFolder();
        MavenProject project = new MavenProject();
        project.getModel().setGroupId( "com.foo" );
        project.getModel().setArtifactId( "myProject" );
        project.getModel().setName( "My Project" );
        project.getModel().setDescription( "My sample project" );
        project.getModel().setVersion( "2.1" );
        File pom = new File( baseDir, "pom.xml" );
        pom.createNewFile();
        project.setFile( pom );
        Properties props =
            new MavenProjectConverter( true ).configure( Arrays.asList( project ), project, new Properties() );
        assertThat( props.getProperty( "sonar.projectKey" ) ).isEqualTo( "com.foo:myProject" );
        assertThat( props.getProperty( "sonar.projectName" ) ).isEqualTo( "My Project" );
        assertThat( props.getProperty( "sonar.projectVersion" ) ).isEqualTo( "2.1" );
        assertThat( props.getProperty( "sonar.sources" ) ).contains( "pom.xml" );
    }

    @Test
    public void convertMultiModuleProject()
        throws Exception
    {
        File baseDir = temp.newFolder();
        MavenProject root = new MavenProject();
        root.getModel().setGroupId( "com.foo" );
        root.getModel().setArtifactId( "myProject" );
        root.getModel().setName( "My Project" );
        root.getModel().setDescription( "My sample project" );
        root.getModel().setVersion( "2.1" );
        root.setFile( new File( baseDir, "pom.xml" ) );

        MavenProject module1 = new MavenProject();
        module1.getModel().setGroupId( "com.foo" );
        module1.getModel().setArtifactId( "module1" );
        module1.getModel().setName( "My Project - Module 1" );
        module1.getModel().setDescription( "My sample project - Module 1" );
        module1.getModel().setVersion( "2.1" );
        File module1BaseDir = new File( baseDir, "module1" );
        module1BaseDir.mkdir();
        module1.setFile( new File( module1BaseDir, "pom.xml" ) );
        module1.setParent( root );
        root.getModules().add( "module1" );

        MavenProject module11 = new MavenProject();
        module11.getModel().setGroupId( "com.foo" );
        module11.getModel().setArtifactId( "module11" );
        module11.getModel().setName( "My Project - Module 1 - Module 1" );
        module11.getModel().setDescription( "My sample project - Module 1 - Module 1" );
        module11.getModel().setVersion( "2.1" );
        File module11BaseDir = new File( module1BaseDir, "module1" );
        module11BaseDir.mkdir();
        module11.setFile( new File( baseDir, "module1/module1/pom.xml" ) );
        module11.setParent( module1 );
        module1.getModules().add( "module1" );

        MavenProject module12 = new MavenProject();
        module12.getModel().setGroupId( "com.foo" );
        module12.getModel().setArtifactId( "module12" );
        module12.getModel().setName( "My Project - Module 1 - Module 2" );
        module12.getModel().setDescription( "My sample project - Module 1 - Module 2" );
        module12.getModel().setVersion( "2.1" );
        File module12BaseDir = new File( module1BaseDir, "module2" );
        module12BaseDir.mkdir();
        module12.setFile( new File( baseDir, "module1/module2/pom.xml" ) );
        module12.setParent( module1 );
        module1.getModules().add( "module2" );

        MavenProject module2 = new MavenProject();
        module2.getModel().setGroupId( "com.foo" );
        module2.getModel().setArtifactId( "module2" );
        module2.getModel().setName( "My Project - Module 2" );
        module2.getModel().setDescription( "My sample project - Module 2" );
        module2.getModel().setVersion( "2.1" );
        File module2BaseDir = new File( baseDir, "module2" );
        module2BaseDir.mkdir();
        module2.setFile( new File( module2BaseDir, "pom.xml" ) );
        module2.setParent( root );
        root.getModules().add( "module2" );

        Properties props =
            new MavenProjectConverter( false ).configure( Arrays.asList( module12, module11, module1, module2, root ),
                                                          root, new Properties() );

        assertThat( props.getProperty( "sonar.projectKey" ) ).isEqualTo( "com.foo:myProject" );
        assertThat( props.getProperty( "sonar.projectName" ) ).isEqualTo( "My Project" );
        assertThat( props.getProperty( "sonar.projectVersion" ) ).isEqualTo( "2.1" );

        assertThat( props.getProperty( "sonar.projectBaseDir" ) ).isEqualTo( baseDir.getAbsolutePath() );

        String module1Key = "com.foo:module1";
        String module2Key = "com.foo:module2";
        assertThat( props.getProperty( "sonar.modules" ).split( "," ) ).containsOnly( module1Key, module2Key );

        String module11Key = "com.foo:module11";
        String module12Key = "com.foo:module12";
        assertThat( props.getProperty( module1Key + ".sonar.modules" ).split( "," ) ).containsOnly( module11Key,
                                                                                                    module12Key );

        assertThat( props.getProperty( module1Key + ".sonar.projectBaseDir" ) ).isEqualTo( module1BaseDir.getAbsolutePath() );
        assertThat( props.getProperty( module1Key + "." + module11Key + ".sonar.projectBaseDir" ) ).isEqualTo( module11BaseDir.getAbsolutePath() );
        assertThat( props.getProperty( module1Key + "." + module12Key + ".sonar.projectBaseDir" ) ).isEqualTo( module12BaseDir.getAbsolutePath() );
        assertThat( props.getProperty( module2Key + ".sonar.projectBaseDir" ) ).isEqualTo( module2BaseDir.getAbsolutePath() );
    }

    @Test
    public void overrideSourcesSingleModuleProject()
        throws Exception
    {
        temp.newFolder( "src" );
        File srcMainDir = temp.newFolder( "src/main" ).getCanonicalFile();
        File pom = temp.newFile( "pom.xml" );

        Properties pomProps = new Properties();
        pomProps.put( "sonar.sources", "src/main" );

        MavenProject project = new MavenProject();
        project.getModel().setGroupId( "com.foo" );
        project.getModel().setArtifactId( "myProject" );
        project.getModel().setName( "My Project" );
        project.getModel().setDescription( "My sample project" );
        project.getModel().setVersion( "2.1" );
        project.getModel().setProperties( pomProps );
        project.setFile( pom );

        Properties props =
            new MavenProjectConverter( false ).configure( Arrays.asList( project ), project, new Properties() );

        assertThat( props.getProperty( "sonar.projectKey" ) ).isEqualTo( "com.foo:myProject" );
        assertThat( props.getProperty( "sonar.projectName" ) ).isEqualTo( "My Project" );
        assertThat( props.getProperty( "sonar.projectVersion" ) ).isEqualTo( "2.1" );

        assertThat( props.getProperty( "sonar.sources" ) ).isEqualTo( srcMainDir.getAbsolutePath() );
    }

    @Test
    public void overrideSourcesMultiModuleProject()
        throws Exception
    {
        Properties pomProps = new Properties();
        pomProps.put( "sonar.sources", "src/main" );
        pomProps.put( "sonar.tests", "src/test" );

        File pomRoot = temp.newFile( "pom.xml" );
        MavenProject root = new MavenProject();
        root.getModel().setGroupId( "com.foo" );
        root.getModel().setArtifactId( "myProject" );
        root.getModel().setName( "My Project" );
        root.getModel().setDescription( "My sample project" );
        root.getModel().setVersion( "2.1" );
        root.getModel().setPackaging( "pom" );
        root.getModel().setProperties( pomProps );
        root.setFile( pomRoot );

        File module1BaseDir = temp.newFolder( "module1" ).getCanonicalFile();
        File module1SrcDir = temp.newFolder( "module1", "src", "main" ).getCanonicalFile();
        File module1TestDir = temp.newFolder( "module1", "src", "test" ).getCanonicalFile();
        MavenProject module1 = new MavenProject();
        module1.getModel().setGroupId( "com.foo" );
        module1.getModel().setArtifactId( "module1" );
        module1.getModel().setName( "My Project - Module 1" );
        module1.getModel().setDescription( "My sample project - Module 1" );
        module1.getModel().setVersion( "2.1" );
        module1.getModel().setProperties( pomProps );
        module1.setFile( new File( module1BaseDir, "pom.xml" ) );
        module1.setParent( root );
        root.getModules().add( "module1" );

        File module2BaseDir = temp.newFolder( "module2" ).getCanonicalFile();
        File module2SrcDir = temp.newFolder( "module2", "src", "main" ).getCanonicalFile();
        File module2TestDir = temp.newFolder( "module2", "src", "test" ).getCanonicalFile();
        MavenProject module2 = new MavenProject();
        module2.getModel().setGroupId( "com.foo" );
        module2.getModel().setArtifactId( "module2" );
        module2.getModel().setName( "My Project - Module 2" );
        module2.getModel().setDescription( "My sample project - Module 2" );
        module2.getModel().setVersion( "2.1" );
        module2.getModel().setProperties( pomProps );
        module2.setFile( new File( module2BaseDir, "pom.xml" ) );
        module2.setParent( root );
        root.getModules().add( "module2" );

        Properties props =
            new MavenProjectConverter( false ).configure( Arrays.asList( module1, module2, root ), root,
                                                          new Properties() );

        assertThat( props.getProperty( "sonar.projectKey" ) ).isEqualTo( "com.foo:myProject" );
        assertThat( props.getProperty( "sonar.projectName" ) ).isEqualTo( "My Project" );
        assertThat( props.getProperty( "sonar.projectVersion" ) ).isEqualTo( "2.1" );

        assertThat( props.getProperty( "sonar.projectBaseDir" ) ).isEqualTo( temp.getRoot().getAbsolutePath() );

        String module1Key = "com.foo:module1";
        String module2Key = "com.foo:module2";
        assertThat( props.getProperty( "sonar.modules" ).split( "," ) ).containsOnly( module1Key, module2Key );

        assertThat( props.getProperty( module1Key + ".sonar.projectBaseDir" ) ).isEqualTo( module1BaseDir.getAbsolutePath() );
        assertThat( props.getProperty( module2Key + ".sonar.projectBaseDir" ) ).isEqualTo( module2BaseDir.getAbsolutePath() );

        assertThat( props.getProperty( "sonar.sources" ) ).isEqualTo( "" );
        assertThat( props.getProperty( "sonar.tests" ) ).isNull();
        assertThat( props.getProperty( module1Key + ".sonar.sources" ) ).isEqualTo( module1SrcDir.getAbsolutePath() );
        assertThat( props.getProperty( module1Key + ".sonar.tests" ) ).isEqualTo( module1TestDir.getAbsolutePath() );
        assertThat( props.getProperty( module2Key + ".sonar.sources" ) ).isEqualTo( module2SrcDir.getAbsolutePath() );
        assertThat( props.getProperty( module2Key + ".sonar.tests" ) ).isEqualTo( module2TestDir.getAbsolutePath() );
    }

    @Test( expected = MojoExecutionException.class )
    public void overrideSourcesNonexistentFolder()
        throws Exception
    {
        File pom = temp.newFile( "pom.xml" );

        Properties pomProps = new Properties();
        pomProps.put( "sonar.sources", "nonexistent-folder" );

        MavenProject project = new MavenProject();
        project.getModel().setGroupId( "com.foo" );
        project.getModel().setArtifactId( "myProject" );
        project.getModel().setName( "My Project" );
        project.getModel().setDescription( "My sample project" );
        project.getModel().setVersion( "2.1" );
        project.getModel().setProperties( pomProps );
        project.setFile( pom );

        new MavenProjectConverter( false ).configure( Arrays.asList( project ), project, new Properties() );
    }

    @Test
    public void overrideProjectKeySingleModuleProject()
        throws Exception
    {
        temp.newFolder( "src" );
        File pom = temp.newFile( "pom.xml" );

        Properties pomProps = new Properties();
        pomProps.put( "sonar.projectKey", "myProject" );

        MavenProject project = new MavenProject();
        project.getModel().setGroupId( "com.foo" );
        project.getModel().setArtifactId( "myProject" );
        project.getModel().setName( "My Project" );
        project.getModel().setDescription( "My sample project" );
        project.getModel().setVersion( "2.1" );
        project.getModel().setProperties( pomProps );
        project.setFile( pom );

        Properties props =
            new MavenProjectConverter( false ).configure( Arrays.asList( project ), project, new Properties() );

        assertThat( props.getProperty( "sonar.projectKey" ) ).isEqualTo( "myProject" );
        assertThat( props.getProperty( "sonar.projectName" ) ).isEqualTo( "My Project" );
        assertThat( props.getProperty( "sonar.projectVersion" ) ).isEqualTo( "2.1" );
    }
}
