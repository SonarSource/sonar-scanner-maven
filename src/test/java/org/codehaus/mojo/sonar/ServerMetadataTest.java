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

import org.junit.Test;

import java.io.IOException;

import static org.fest.assertions.Assertions.assertThat;

public class ServerMetadataTest
{
    private static final String URL = "http://test";

    @Test
    public void shouldReturnAValidResult()
        throws IOException
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

        assertThat( server.remoteContent( "an action" ) ).isEqualTo( validContent );
    }

    @Test
    public void shouldRemoveLastUrlSlash()
    {
        ServerMetadata server = new ServerMetadata( "http://test/" );
        assertThat( server.getUrl() ).isEqualTo( URL );
    }

    @Test( expected = IllegalStateException.class )
    public void shouldFailIfCanNotConnectToServer()
    {
        ServerMetadata server = new ServerMetadata( "http://unknown.foo" );
        server.getVersion();
    }

    @Test
    public void testSonarVersionPrior2Dot4()
    {
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "1.12" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "2.0.1" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "2.1" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "2.1.2" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "2.2" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "2.3" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "2.3.1" ) ).isTrue();

        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "2.4" ) ).isFalse();
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "2.10" ) ).isFalse();
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "2.10" ) ).isFalse();
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "2.11" ) ).isFalse();
        assertThat( ServerMetadata.isVersionPriorTo2Dot4( "3.0" ) ).isFalse();
    }

    @Test
    public void testSonarVersionPrior3Dot7()
    {
        assertThat( ServerMetadata.isVersionPriorTo3Dot7( "1.12" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo3Dot7( "2.0.1" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo3Dot7( "2.1" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo3Dot7( "2.4" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo3Dot7( "2.11" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo3Dot7( "3.0" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo3Dot7( "3.1" ) ).isTrue();
        assertThat( ServerMetadata.isVersionPriorTo3Dot7( "3.6" ) ).isTrue();

        assertThat( ServerMetadata.isVersionPriorTo3Dot7( "3.7" ) ).isFalse();
        assertThat( ServerMetadata.isVersionPriorTo3Dot7( "4.0" ) ).isFalse();
    }

}
