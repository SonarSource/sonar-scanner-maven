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
