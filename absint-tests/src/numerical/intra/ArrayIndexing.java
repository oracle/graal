public class ArrayIndexing {
  public static void main(String[] args) {
    int[] a = new int[5];
    for (int i = 0; i < a.length; i++) {
      a[i] = i * 2; // 0,2,4,6,8
    }

    int idx = 2;
    int val = a[idx];
    System.out.println("val=" + val);

    // index changed by branch
    if (val > 3) {
      idx = 4;
    } else {
      idx = 0;
    }
    System.out.println("a[idx]=" + a[idx]);

    // boundary condition: use computed index
    int computed = (a[1] / 2) + 1; // (2/2)+1 = 2
    System.out.println("computed=" + computed + ", a[computed]=" + a[computed]);
  }
}
