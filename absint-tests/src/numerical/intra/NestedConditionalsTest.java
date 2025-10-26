public class NestedConditionalsTest {
  public static void main(String[] args) {
    int a = 3;
    int b = 4;
    int res = 0;

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
    System.out.println("res=" + res);

    // nested condition with reassignment
    int x = 0;
    if (res % 2 == 0) {
      x = res / 2;
      if (x > 3) {
        x = x + 10;
      }
    } else {
      x = -res;
    }
    System.out.println("x=" + x);
  }
}
