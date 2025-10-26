public class RecursiveSum {
  static int sumToN(int n) {
    if (n == 0) return 0;
    return n + sumToN(n - 1);
  }

  public static void main(String[] args) {
    int s = sumToN(5);
  }
}
