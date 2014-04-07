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

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ServerMetadata
{

    public static final int CONNECT_TIMEOUT_MILLISECONDS = 30000;

    public static final int READ_TIMEOUT_MILLISECONDS = 60000;

    private String url;

    private String version;

    public ServerMetadata( String url )
    {
        if ( url.endsWith( "/" ) )
        {
            this.url = url.substring( 0, url.length() - 1 );
        }
        else
        {
            this.url = url;
        }
    }

    public String getVersion()
        throws IOException
    {
        if ( version == null )
        {
            version = remoteContent( "/api/server/version" );
        }
        return version;
    }

    public String getUrl()
    {
        return url;
    }

    public void logSettings( Log log )
        throws MojoExecutionException
    {
        try
        {
            log.info( "SonarQube version: " + getVersion() );

        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "SonarQube server can not be reached at " + url
                + ". Please check the parameter 'sonar.host.url'.", e );
        }
    }

    protected String remoteContent( String path )
        throws IOException
    {
        String fullUrl = url + path;
        HttpURLConnection conn = getConnection( fullUrl, "GET" );
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

    static HttpURLConnection getConnection( String url, String method )
        throws IOException
    {
        URL page = new URL( url );
        HttpURLConnection conn = (HttpURLConnection) page.openConnection();
        HttpsTrust.INSTANCE.trust( conn );
        conn.setConnectTimeout( CONNECT_TIMEOUT_MILLISECONDS );
        conn.setReadTimeout( READ_TIMEOUT_MILLISECONDS );
        conn.setRequestMethod( method );
        conn.connect();
        return conn;
    }

    protected boolean supportsMaven3()
        throws IOException
    {
        return !isVersionPriorTo2Dot4( getVersion() );
    }

    protected boolean supportsMaven3_1()
        throws IOException
    {
        return !isVersionPriorTo3Dot7( getVersion() );
    }

    protected boolean supportsSonarQubeRunnerBootstrappingFromMaven()
        throws IOException
    {
        return !isVersionPriorTo4Dot3( getVersion() );
    }

    protected static boolean isVersionPriorTo2Dot4( String version )
    {
        ArtifactVersion artifactVersion = new DefaultArtifactVersion( version );
        return artifactVersion.getMajorVersion() < 2 || artifactVersion.getMajorVersion() == 2
            && artifactVersion.getMinorVersion() < 4;
    }

    protected static boolean isVersionPriorTo3Dot7( String version )
    {
        ArtifactVersion artifactVersion = new DefaultArtifactVersion( version );
        return artifactVersion.getMajorVersion() < 3 || artifactVersion.getMajorVersion() == 3
            && artifactVersion.getMinorVersion() < 7;
    }

    protected static boolean isVersionPriorTo4Dot3( String version )
    {
        ArtifactVersion artifactVersion = new DefaultArtifactVersion( version );
        return artifactVersion.getMajorVersion() < 4 || artifactVersion.getMajorVersion() == 4
            && artifactVersion.getMinorVersion() < 3;
    }
}
