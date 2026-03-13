public class ArrayIndexing {
  private static int val;
  private static int computed;
  public static void main(String[] args) {
    int[] a = new int[5];
    for (int i = 0; i < a.length; i++) {
      a[i] = i * 2; 
    }

    int idx = 2;
    val = a[idx];

    if (val > 3) {
      idx = 4;
    } else {
      idx = 0;
    }

    computed = (a[1] / 2) + 1;
  }
}
