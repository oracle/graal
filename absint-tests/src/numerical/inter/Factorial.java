public class Factorial {
  private static int y = 0;

  private static int fact(int n) {
    if (n <= 1) return 1;
    return n * fact(n - 1);
  }

  public static void main(String[] args) {
    y = fact(5);
    System.out.println(y);
  }

}
