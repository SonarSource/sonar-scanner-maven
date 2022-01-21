/*
 * SonarQube Scanner for Maven
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
package org.sonarsource.scanner.maven.bootstrap;

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
}
