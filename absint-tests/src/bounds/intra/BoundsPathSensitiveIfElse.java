public class BoundsPathSensitiveIfElse {
  public static void main(String[] args) {
    int[] a = new int[8];
    int i = args.length - 1; // abstractly, could be negative or up to some small number

    if (i >= 0 && i < a.length) {
      // On this path, index is definitely within [0, a.length-1].
      a[i] = 1; // always safe on the then-branch.
    } else {
      // Here, we only know that !(0 <= i < a.length), so i < 0 or i >= a.length.
      // This access is potentially unsafe and should be flagged.
      // Depending on analysis precision, it might be classified as definitely out-of-bounds
      // or at least unknown.
       a[i] = 2;
    }

    if (i >= 0 && i < a.length) {
      // This condition is not always true at this point (because we have both branches),
      // but it is exactly the same as the guard above.
      // Interval analysis may classify this as unknown in general.
      System.out.println("maybe");
    }
  }
}

