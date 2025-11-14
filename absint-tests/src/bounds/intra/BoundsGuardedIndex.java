// Intra-procedural: access guarded by explicit 0 <= idx < length checks
public class BoundsGuardedIndex {
  private static int unknown() { return (int)(System.currentTimeMillis() & 7); }

  public static void main(String[] args) {
    int[] a = new int[8];
    for (int i = 0; i < a.length; i++) a[i] = i * 3;

    int idx = unknown(); // some unknown in [0,7]
    int v;
    if (idx >= 0 && idx < a.length) {
      v = a[idx]; // should be recognized as safe after guard
    } else {
      v = -1;
    }
    if (v == 123456) System.out.println("unlikely");
  }
}
