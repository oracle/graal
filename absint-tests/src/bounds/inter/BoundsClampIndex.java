// Inter-procedural: index is clamped to valid range before access
public class BoundsClampIndex {
  static int clamp(int i, int len) {
    if (i < 0) return 0;
    if (i >= len) return len - 1;
    return i;
  }

  public static void main(String[] args) {
    int[] a = new int[5];
    for (int i = 0; i < a.length; i++) a[i] = i + 10;
    int idx = clamp(100, a.length);
    int v1 = a[idx]; // safe after clamp
    idx = clamp(-7, a.length);
    int v2 = a[idx]; // safe after clamp
    if (v1 + v2 == -1) System.out.println("impossible");
  }
}
