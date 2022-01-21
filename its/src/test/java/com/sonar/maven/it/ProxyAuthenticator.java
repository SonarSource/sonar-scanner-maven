/*
 * SonarSource :: IT :: SonarQube Maven
 * Copyright (C) 2009-2022 SonarSource SA
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.DeferredAuthentication;
import org.eclipse.jetty.security.authentication.LoginAuthenticator;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Authentication.User;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.security.Constraint;

/**
 * Inspired from {@link BasicAuthenticator} but adapted for proxy auth.
 */
public class ProxyAuthenticator extends LoginAuthenticator {
  /* ------------------------------------------------------------ */
  public ProxyAuthenticator() {
  }

  /* ------------------------------------------------------------ */
  /**
   * @see org.eclipse.jetty.security.Authenticator#getAuthMethod()
   */
  @Override
  public String getAuthMethod() {
    return Constraint.__BASIC_AUTH;
  }

  /* ------------------------------------------------------------ */
  /**
   * @see org.eclipse.jetty.security.Authenticator#validateRequest(javax.servlet.ServletRequest, javax.servlet.ServletResponse, boolean)
   */
  @Override
  public Authentication validateRequest(ServletRequest req, ServletResponse res, boolean mandatory) throws ServerAuthException {
    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) res;
    String credentials = request.getHeader(HttpHeader.PROXY_AUTHORIZATION.asString());

    try {
      if (!mandatory)
        return new DeferredAuthentication(this);

      if (credentials != null) {
        int space = credentials.indexOf(' ');
        if (space > 0) {
          String method = credentials.substring(0, space);
          if ("basic".equalsIgnoreCase(method)) {
            credentials = credentials.substring(space + 1);
            credentials = B64Code.decode(credentials, StandardCharsets.ISO_8859_1);
            int i = credentials.indexOf(':');
            if (i > 0) {
              String username = credentials.substring(0, i);
              String password = credentials.substring(i + 1);

              UserIdentity user = login(username, password, request);
              if (user != null) {
                return new UserAuthentication(getAuthMethod(), user);
              }
            }
          }
        }
      }

      if (DeferredAuthentication.isDeferred(response))
        return Authentication.UNAUTHENTICATED;

      response.setHeader(HttpHeader.PROXY_AUTHENTICATE.asString(), "basic realm=\"" + _loginService.getName() + '"');
      response.sendError(HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED);
      return Authentication.SEND_CONTINUE;
    } catch (IOException e) {
      throw new ServerAuthException(e);
    }
  }

  @Override
  public boolean secureResponse(ServletRequest req, ServletResponse res, boolean mandatory, User validatedUser) throws ServerAuthException {
    return true;
  }

}
