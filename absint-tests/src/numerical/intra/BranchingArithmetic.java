public class BranchingArithmetic {
  public static void main(String[] args) {
    int x = 7;
    int y;

    if (x % 2 == 0) {
      y = x / 2;
    } else {
      y = 3 * x + 1;
    }
    System.out.println("y=" + y);

    // another branch that produces new definitions
    int z = 0;
    if (x > 10) {
      z = x - 10;
    } else if (x > 5) {
      z = x + 5;
    } else {
      z = x * 2;
    }
    System.out.println("z=" + z);
  }
}
