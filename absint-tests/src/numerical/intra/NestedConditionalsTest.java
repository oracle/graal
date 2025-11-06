public class NestedConditionalsTest {
  private static int res = 0;
  private static int x = 0;

  public static void main(String[] args) {
    int a = 3;
    int b = 4;

    if (a > 0) {
      if (b > 0) {
        res = a + b; // 7
      } else {
        res = a - b;
      }
    } else {
      if (b > 10) {
        res = a * b;
      } else {
        res = b - a;
      }
    }

    // nested condition with reassignment
    if (res % 2 == 0) {
      x = res / 2;
      if (x > 3) {
        x = x + 10;
      }
    } else {
      x = -res;
    }
  }
}
