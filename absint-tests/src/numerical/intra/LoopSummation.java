public class LoopSummation {
  private static int res = 0;

  public static void main(String[] args) {
    int n = 5;
    int sum = 0;

    // simple loop with incremental update
    for (int i = 0; i < n; i++) {
      res += i;
    }

  }
}
