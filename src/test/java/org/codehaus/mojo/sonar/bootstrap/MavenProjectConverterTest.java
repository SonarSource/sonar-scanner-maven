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
        Properties props = new MavenProjectConverter().configure( Arrays.asList( project ), project );
        assertThat( props.getProperty( "sonar.projectKey" ) ).isEqualTo( "com.foo:myProject" );
        assertThat( props.getProperty( "sonar.projectName" ) ).isEqualTo( "My Project" );
        assertThat( props.getProperty( "sonar.projectVersion" ) ).isEqualTo( "2.1" );
    }
}
