public class BoundsAlwaysSafeSimple {
  public static void main(String[] args) {
    int[] a = new int[4];
    for (int i = 0; i < a.length; i++) {
      // At this point, an interval analysis should infer i in [0, a.length-1]
      // so the following access is always in-bounds.
      a[i] = i;
      if (!(0 <= i && i < a.length)) {
        // This branch should be unreachable (condition always false).
        System.out.println("unreachable");
      }
      if (i >= a.length) {
        // Also unreachable: i >= a.length is always false in the loop body.
        System.out.println("also unreachable");
      }
    }

    int sum = 0;
    for (int i = 0; i < a.length; i++) {
      // Again, always in-bounds.
      sum += a[i];
    }
    if (sum == 123456) System.out.println("unlikely");
  }
}

