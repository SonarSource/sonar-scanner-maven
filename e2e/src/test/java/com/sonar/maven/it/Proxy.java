/*
 * SonarSource :: E2E :: SonarQube Maven
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
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
package com.sonar.maven.it;

import com.sonar.orchestrator.util.NetworkUtils;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.proxy.ProxyHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class Proxy {
  private static final String PROXY_USER = "scott";
  private static final String PROXY_PASSWORD = "tiger";
  private static final String PROXY_CREDENTIALS = "Basic " + Base64.getEncoder()
    .encodeToString((PROXY_USER + ":" + PROXY_PASSWORD).getBytes(StandardCharsets.ISO_8859_1));
  private Server server;
  private int httpProxyPort;

  private static ConcurrentLinkedDeque<String> seenByProxy = new ConcurrentLinkedDeque<>();

  public void stopProxy() throws Exception {
    if (server != null && server.isStarted()) {
      server.stop();
    }
  }

  public int port() {
    return httpProxyPort;
  }

  public Collection<String> seen() {
    return new ArrayList<>(seenByProxy);
  }

  public int startProxy() throws Exception {
    seenByProxy.clear();
    InetAddress address = InetAddress.getLoopbackAddress();
    httpProxyPort = NetworkUtils.getNextAvailablePort(address);

    // Setup Threadpool
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(500);

    server = new Server(threadPool);

    // HTTP Configuration
    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setSecureScheme("https");
    httpConfig.setSendServerVersion(true);
    httpConfig.setSendDateHeader(false);

    server.setHandler(proxyHandler());

    ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
    http.setPort(httpProxyPort);
    server.addConnector(http);
    server.start();

    return httpProxyPort;
  }

  private Handler proxyHandler() {
    return new Handler.Wrapper(new ProxyHandler.Forward()) {
      @Override
      public boolean handle(Request request, Response response, Callback callback) throws Exception {
        if (!PROXY_CREDENTIALS.equals(request.getHeaders().get(HttpHeader.PROXY_AUTHORIZATION))) {
          response.getHeaders().put(HttpHeader.PROXY_AUTHENTICATE, "Basic realm=\"myrealm\"");
          Response.writeError(request, response, callback, HttpStatus.PROXY_AUTHENTICATION_REQUIRED_407);
          return true;
        }

        seenByProxy.add(request.getHttpURI().toString());
        return super.handle(request, response, callback);
      }
    };
  }
}
