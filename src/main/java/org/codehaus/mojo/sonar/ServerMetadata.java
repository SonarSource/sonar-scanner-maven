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
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ServerMetadata
{

    public static final int CONNECT_TIMEOUT_MILLISECONDS = 30000;

    public static final int READ_TIMEOUT_MILLISECONDS = 60000;

    public static final String MAVEN_PATH = "/deploy/maven";

    private String url;

    private String version;

    private String key;

    public ServerMetadata( String url )
    {
        this.url = StringUtils.chompLast( url, "/" );
    }

    public void connect()
        throws MojoExecutionException
    {
        try
        {
            this.version = remoteContent( "/api/server/version" );
            this.key = remoteContent( "/api/server/key" );

        }
        catch ( IOException e )
        {
            throw new MojoExecutionException(
                "Sonar server can not be reached. Please check the parameter 'sonar.host.url': " + this.url );
        }
    }

    public String getVersion()
    {
        return version;
    }

    public String getKey()
    {
        return key;
    }

    public String getMavenRepositoryUrl()
    {
        return url + MAVEN_PATH;
    }

    public String getUrl()
    {
        return url;
    }

    public boolean needsSonarInternalRepository()
    {
        return isVersionPriorTo2Dot2( version );
    }

    static boolean isVersionPriorTo2Dot2( String version )
    {
        return version.startsWith( "1." ) || version.startsWith( "2.0" ) || "2.1".equals( version ) ||
            version.startsWith( "2.1." );
    }

    public void logSettings( Log log )
    {
        log.info( "Sonar host: " + getUrl() );
        log.info( "Sonar version: " + getVersion() );
    }

    protected String remoteContent( String path )
        throws IOException
    {
        String fullUrl = url + path;
        HttpURLConnection conn = getConnection( fullUrl + path, "GET" );
        InputStream input = (InputStream) conn.getContent();
        try
        {
            int statusCode = conn.getResponseCode();
            if ( statusCode != HttpURLConnection.HTTP_OK )
            {
                throw new IOException( "Status returned by url : '" + fullUrl + "' is invalid : " + statusCode );
            }
            return IOUtil.toString( input );

        }
        finally
        {
            IOUtil.close( input );
            conn.disconnect();
        }
    }

    protected HttpURLConnection getConnection( String url, String method )
        throws IOException
    {
        URL page = new URL( url );
        HttpURLConnection conn = (HttpURLConnection) page.openConnection();
        conn.setConnectTimeout( CONNECT_TIMEOUT_MILLISECONDS );
        conn.setReadTimeout( READ_TIMEOUT_MILLISECONDS );
        conn.setRequestMethod( method );
        conn.connect();
        return conn;
    }

}
