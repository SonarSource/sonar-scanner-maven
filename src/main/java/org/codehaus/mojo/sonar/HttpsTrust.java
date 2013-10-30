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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

class HttpsTrust
{

    static HttpsTrust INSTANCE = new HttpsTrust( new Ssl() );

    static class Ssl
    {
        SSLSocketFactory newFactory( TrustManager... managers )
            throws NoSuchAlgorithmException, KeyManagementException
        {
            SSLContext context = SSLContext.getInstance( "TLS" );
            context.init( null, managers, new SecureRandom() );
            return context.getSocketFactory();
        }
    }

    private final SSLSocketFactory socketFactory;

    private final HostnameVerifier hostnameVerifier;

    HttpsTrust( Ssl context )
    {
        this.socketFactory = createSocketFactory( context );
        this.hostnameVerifier = createHostnameVerifier();
    }

    void trust( HttpURLConnection connection )
    {
        if ( connection instanceof HttpsURLConnection )
        {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            httpsConnection.setSSLSocketFactory( socketFactory );
            httpsConnection.setHostnameVerifier( hostnameVerifier );
        }
    }

    /**
     * Trust all certificates
     */
    private SSLSocketFactory createSocketFactory( Ssl context )
    {
        try
        {
            return context.newFactory( new AlwaysTrustManager() );
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( "Fail to build SSL factory", e );
        }
    }

    /**
     * Trust all hosts
     */
    private HostnameVerifier createHostnameVerifier()
    {
        return new HostnameVerifier()
        {
            public boolean verify( String hostname, SSLSession session )
            {
                return true;
            }
        };
    }

    static class AlwaysTrustManager
        implements X509TrustManager
    {
        public X509Certificate[] getAcceptedIssuers()
        {
            return new X509Certificate[0];
        }

        public void checkClientTrusted( X509Certificate[] chain, String authType )
        {
            // Do not check
        }

        public void checkServerTrusted( X509Certificate[] chain, String authType )
        {
            // Do not check
        }
    }
}
