/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.scanner.maven.bootstrap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenUtilsTest {
  @Test
  public void testCoalesce() {
    Object o1 = null;
    Object o2 = null;
    Object o3 = new Object();

    assertThat(MavenUtils.coalesce(o1, o2)).isNull();
    assertThat(MavenUtils.coalesce(o1, o3, o2)).isEqualTo(o3);
  }

  @Test
  public void testJoinAsCsv() {
    List<String> values = Arrays.asList("/home/users/me/artifact-123,456.jar", "/opt/lib");
    assertThat(MavenUtils.joinAsCsv(values)).isEqualTo("\"/home/users/me/artifact-123,456.jar\",/opt/lib");

    values = Arrays.asList("/opt/lib", "/home/users/me/artifact-123,456.jar");
    assertThat(MavenUtils.joinAsCsv(values)).isEqualTo("/opt/lib,\"/home/users/me/artifact-123,456.jar\"");

    values = Arrays.asList("/opt/lib", "/home/users/me");
    assertThat(MavenUtils.joinAsCsv(values)).isEqualTo("/opt/lib,/home/users/me");
  }
}
