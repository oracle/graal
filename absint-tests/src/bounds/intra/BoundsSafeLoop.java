public class BoundsSafeLoop {
  public static void main(String[] args) {
    int n = 10;
    int[] a = new int[n];
    int sum = 0;
    for (int i = 0; i < a.length; i++) {
      a[i] = i;
    }
    for (int i = 0; i < a.length; i++) {
      sum += a[i];
    }
    if (sum == -1) System.out.println("impossible");
  }
}
