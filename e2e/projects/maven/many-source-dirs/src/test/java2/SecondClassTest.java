import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SecondClassTest {
  @Test
  public void hello() {
    assertEquals("hello", new SecondClass().foo());
  }
}
