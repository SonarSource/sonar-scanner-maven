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
package org.sonarsource.scanner.maven;


import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.maven.bootstrap.MavenCompilerResolver.MavenCompilerConfiguration;

class MavenCompilerResolverTest {

  @Test
  void testSameCompilerConfiguration() {
    MavenCompilerConfiguration conf1 = mock(MavenCompilerConfiguration.class);
    MavenCompilerConfiguration conf2 = mock(MavenCompilerConfiguration.class);

    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isTrue();
    when(conf1.getJdkHome()).thenReturn(Optional.of("JDK_8"));
    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isFalse();
    when(conf2.getJdkHome()).thenReturn(Optional.of("JDK_8"));
    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isTrue();
    when(conf1.getRelease()).thenReturn(Optional.of("8"));
    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isFalse();
    when(conf2.getRelease()).thenReturn(Optional.of("8"));
    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isTrue();
    when(conf1.getSource()).thenReturn(Optional.of("1.8"));
    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isFalse();
    when(conf2.getSource()).thenReturn(Optional.of("1.8"));
    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isTrue();
    when(conf1.getTarget()).thenReturn(Optional.of("8"));
    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isFalse();
    when(conf2.getTarget()).thenReturn(Optional.of("8"));
    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isTrue();
    when(conf1.getEnablePreview()).thenReturn(Optional.of("true"));
    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isFalse();
    when(conf2.getEnablePreview()).thenReturn(Optional.of("true"));
    assertThat(MavenCompilerConfiguration.same(conf1, conf2)).isTrue();
  }

}
