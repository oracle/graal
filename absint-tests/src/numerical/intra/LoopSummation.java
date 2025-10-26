public class LoopSummation {
  public static void main(String[] args) {
    int n = 5;
    int sum = 0;

    // simple loop with incremental update
    for (int i = 0; i < n; i++) {
      sum = sum + i;
    }
    assert(sum == 15);

    // loop with conditional update
    int s2 = 0;
    for (int i = 0; i < n; i++) {
      if ((i & 1) == 0) {
        s2 += 2 * i;
      } else {
        s2 += i;
      }
    }
  }
}
