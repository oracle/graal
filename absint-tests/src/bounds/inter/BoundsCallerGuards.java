// Inter-procedural: caller ensures index validity, callee assumes
public class BoundsCallerGuards {
  static int uncheckedAt(int[] a, int i) {
    // No guards here; rely on caller to pass a safe index
    return a[i];
  }

  public static void main(String[] args) {
    int[] a = new int[6];
    for (int i = 0; i < a.length; i++) a[i] = i * 2;
    int sum = 0;
    for (int i = 0; i < a.length; i++) {
      // i is in [0, a.length)
      sum += uncheckedAt(a, i); // should be provably safe
    }
    if (sum == -1) System.out.println("impossible");
  }
}
