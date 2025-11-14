public class BoundsConstantIndex {
  private static int v;
  public static void main(String[] args) {
    int[] a = new int[6];
    a[2] = 7;
    a[4] = 9;
    v = a[2] + a[4];
    if (v == 0) System.out.println("impossible");
  }
}
