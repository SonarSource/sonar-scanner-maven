/*
 * Copyright (C) 2011-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package org.codehaus.mojo.sonar.mock;

import org.apache.commons.io.IOUtils;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.apache.commons.io.IOUtils.write;

public final class MockHttpServer {
  private Server server;
  private String responseBody;
  private String requestBody;
  private String mockResponseData;
  private int mockResponseStatus = SC_OK;

  public void start() throws Exception {
    // 0 is random available port
    server = new Server(0);
    server.setHandler(getMockHandler());
    server.start();
  }

  /**
   * Creates an {@link AbstractHandler handler} returning an arbitrary String as a response.
   *
   * @return never <code>null</code>.
   */
  public Handler getMockHandler() {
    Handler handler = new AbstractHandler() {

      public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException {
        Request baseRequest = request instanceof Request ? (Request) request : HttpConnection.getCurrentConnection().getRequest();
        setResponseBody(getMockResponseData());
        setRequestBody(IOUtils.toString(baseRequest.getInputStream()));
        response.setStatus(mockResponseStatus);
        response.setContentType("text/xml;charset=utf-8");
        write(getResponseBody(), response.getOutputStream());
        baseRequest.setHandled(true);
      }
    };
    return handler;
  }

  public void stop() {
    try {
      if (server != null) {
        server.stop();
      }
    } catch (Exception e) {
      throw new IllegalStateException("Fail to stop HTTP server", e);
    }
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setRequestBody(String requestBody) {
    this.requestBody = requestBody;
  }

  public String getRequestBody() {
    return requestBody;
  }

  public void setMockResponseData(String mockResponseData) {
    this.mockResponseData = mockResponseData;
  }

  public void setMockResponseStatus(int status) {
    this.mockResponseStatus = status;
  }

  public String getMockResponseData() {
    return mockResponseData;
  }

  public int getPort() {
    return server.getConnectors()[0].getLocalPort();
  }
}
