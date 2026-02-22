public class BranchingArithmetic {
  private static int y;
  private static int z;
  public static void main(String[] args) {
    int x = 7;

    if (x % 2 == 0) {
      y = x / 2;
    } else {
      y = 3 * x + 1;
    }

    if (x > 10) {
      z = x - 10;
    } else if (x > 5) {
      z = x + 5;
    } else {
      z = x * 2;
    }
  }
}
