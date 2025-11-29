public class BoundsCalleeGuarded {
  static int getOrDefault(int[] a, int i) {
    if (a == null) return -1;
    if (i >= 0 && i < a.length) {
      return a[i];
    }
    return -1;
  }

  public static void main(String[] args) {
    int[] a = new int[4];
    for (int i = 0; i < a.length; i++) a[i] = i + 1;
    int v1 = getOrDefault(a, 2);
    int v2 = getOrDefault(a, -1);
    int v3 = getOrDefault(a, 99);
    if (v1 + v2 + v3 == 123456) System.out.println("unlikely");
  }
}
