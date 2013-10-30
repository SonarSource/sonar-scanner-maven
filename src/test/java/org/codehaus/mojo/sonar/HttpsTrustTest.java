package org.codehaus.mojo.sonar;

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

import org.junit.Test;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpsTrustTest
{
    @Test
    public void trustAllHosts()
        throws Exception
    {
        HttpsURLConnection connection = newHttpsConnection();
        HttpsTrust.INSTANCE.trust( connection );

        assertThat( connection.getHostnameVerifier() ).isNotNull();
        assertThat( connection.getHostnameVerifier().verify( "foo", null ) ).isTrue();
    }

    @Test
    public void singleHostnameVerifier()
        throws Exception
    {
        HttpsURLConnection connection1 = newHttpsConnection();
        HttpsTrust.INSTANCE.trust( connection1 );
        HttpsURLConnection connection2 = newHttpsConnection();
        HttpsTrust.INSTANCE.trust( connection2 );

        assertThat( connection1.getHostnameVerifier() ).isSameAs( connection2.getHostnameVerifier() );
    }

    @Test
    public void trustAllCerts()
        throws Exception
    {
        HttpsURLConnection connection1 = newHttpsConnection();
        HttpsTrust.INSTANCE.trust( connection1 );

        assertThat( connection1.getSSLSocketFactory() ).isNotNull();
        assertThat( connection1.getSSLSocketFactory().getDefaultCipherSuites() ).isNotEmpty();
    }

    @Test
    public void singleSslFactory()
        throws Exception
    {
        HttpsURLConnection connection1 = newHttpsConnection();
        HttpsTrust.INSTANCE.trust( connection1 );
        HttpsURLConnection connection2 = newHttpsConnection();
        HttpsTrust.INSTANCE.trust( connection2 );

        assertThat( connection1.getSSLSocketFactory() ).isSameAs( connection2.getSSLSocketFactory() );
    }

    @Test
    public void testAlwaysTrustManager()
        throws Exception
    {
        HttpsTrust.AlwaysTrustManager manager = new HttpsTrust.AlwaysTrustManager();
        assertThat( manager.getAcceptedIssuers() ).isEmpty();
        // does nothing
        manager.checkClientTrusted( null, null );
        manager.checkServerTrusted( null, null );
    }

    @Test
    public void failOnError()
        throws Exception
    {
        HttpsTrust.Ssl context = mock( HttpsTrust.Ssl.class );
        KeyManagementException cause = new KeyManagementException( "foo" );
        when( context.newFactory( any( TrustManager.class ) ) ).thenThrow( cause );

        try
        {
            new HttpsTrust( context );
            fail();
        }
        catch ( IllegalStateException e )
        {
            assertThat( e.getMessage() ).isEqualTo( "Fail to build SSL factory" );
            assertThat( e.getCause() ).isSameAs( cause );
        }
    }

    private HttpsURLConnection newHttpsConnection()
        throws IOException
    {
        return (HttpsURLConnection) new URL( "https://localhost" ).openConnection();
    }
}

