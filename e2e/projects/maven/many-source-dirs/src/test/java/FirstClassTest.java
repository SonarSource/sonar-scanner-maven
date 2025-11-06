import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FirstClassTest {
  @Test
  public void hello() {
    assertEquals("hello", new FirstClass().hello());
  }
}
