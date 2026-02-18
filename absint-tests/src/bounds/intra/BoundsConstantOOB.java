public class BoundsConstantOOB {
  public static void main(String[] args) {
    int[] a = new int[5];

    int in = 3; // always in-bounds
    int outNeg = -1; // always out-of-bounds (negative)
    int outHigh = 5; // always out-of-bounds (== length)

    // Definitely safe
    a[in] = 1;

    // The following two uses are definitely out-of-bounds.
    // An interval analysis should classify these as always unsafe.
    // (They may still run and throw at runtime if executed.)
    if (outNeg >= 0 && outNeg < a.length) {
      // Guard is always false; body is unreachable.
      a[outNeg] = 2;
    }

    if (outHigh >= 0 && outHigh < a.length) {
      // Guard is always false; body is unreachable.
      a[outHigh] = 3;
    }

    // Unguarded OOB access for analysis to detect.
    // a[outHigh] = 4; // uncomment to create a concrete runtime error

    if (a[in] == 123456) System.out.println("unlikely");
  }
}

