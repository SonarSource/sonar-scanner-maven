/*
 * Copyright (C) 2011-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package org.codehaus.mojo.sonar.mock;

import org.junit.rules.ExternalResource;

public final class MockHttpServerInterceptor extends ExternalResource {

  private MockHttpServer server;

  @Override
  protected final void before() throws Throwable {
    server = new MockHttpServer();
    server.start();
  }

  @Override
  protected void after() {
    server.stop();
  }

  public void setMockResponseData(String data) {
    server.setMockResponseData(data);
  }

  public void setMockResponseStatus(int status) {
    server.setMockResponseStatus(status);
  }

  public int getPort() {
    return server.getPort();
  }
}