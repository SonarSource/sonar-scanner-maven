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

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ServerMetadataTest
{
    private static final String URL = "http://test";


    @Test
    public void shouldReturnAValidResult()
        throws MojoExecutionException, IOException
    {
        final String validContent = "valid";
        ServerMetadata server = new ServerMetadata( URL )
        {

            @Override
            protected String remoteContent( String path )
            {
                return ( validContent );
            }
        };

        assertThat( server.remoteContent( "an action" ), is( validContent ) );
    }

    @Test
    public void shouldRemoveLastUrlSlash()
        throws MojoExecutionException
    {
        ServerMetadata server = new ServerMetadata( "http://test/" );
        assertThat( server.getUrl(), is( URL ) );
    }

    @Test
    public void shouldReturnMavenRepositoryUrl()
        throws MojoExecutionException
    {
        ServerMetadata server = new ServerMetadata( URL );
        assertThat( server.getMavenRepositoryUrl(), is( URL + ServerMetadata.MAVEN_PATH ) );
    }

    @Test( expected = MojoExecutionException.class )
    public void shouldFailIfCanNotConnectToServer()
        throws MojoExecutionException
    {
        ServerMetadata server = new ServerMetadata( "http://unknown.foo" );
        server.connect();
    }

    @Test
    public void checkSonarVersion()
    {
        assertThat( ServerMetadata.isVersionPriorTo2Dot2( "1.12" ), is( true ) );
        assertThat( ServerMetadata.isVersionPriorTo2Dot2( "2.0.1" ), is( true ) );
        assertThat( ServerMetadata.isVersionPriorTo2Dot2( "2.1" ), is( true ) );
        assertThat( ServerMetadata.isVersionPriorTo2Dot2( "2.1.2" ), is( true ) );

        assertThat( ServerMetadata.isVersionPriorTo2Dot2( "2.2" ), is( false ) );
        assertThat( ServerMetadata.isVersionPriorTo2Dot2( "2.10" ), is( false ) );
        assertThat( ServerMetadata.isVersionPriorTo2Dot2( "2.11" ), is( false ) );
        assertThat( ServerMetadata.isVersionPriorTo2Dot2( "3.0" ), is( false ) );
    }
}

