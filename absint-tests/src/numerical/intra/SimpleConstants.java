public class SimpleConstants {
  private static int field = 0;
  public static void main(String[] args) {
    int a = 5;
    int b = -3;
    int c = a + b; // 2
    for (int i = 0; i < c; i++) {
        b += i;
    }
    field = b;
    int d = 0;
    d = (a > 0) ? 1 : 2; // d should be 1

    int e = 10 * 2 - 5; // 15
  }
}
