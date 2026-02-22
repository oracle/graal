public class ArrayBoundsSafe {

  private static int x;
  private static int y;

  public static void main(String[] args) {
    int[] a = new int[5];
    for (int i = 0; i < a.length; i++) {
      a[i] = i * 2; // 0,2,4,6,8
    }

    int idx = 2;
    x = a[idx];
    // Infer that x == [4, 4] always holds here

    if (x > 3) {
      x = 4;
    } else {
      x = 0;
    }

    // Infer that x == [0, 0] always holds here 

    y = (a[1] / 2) + 1;
    // Infer that y == [2, 2] always holds here
    // System.out.println(x);
  }
}
