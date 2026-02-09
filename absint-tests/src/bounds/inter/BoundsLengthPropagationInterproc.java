public class BoundsLengthPropagationInterproc {
  static int mid(int[] a) {
    if (a == null || a.length == 0) return -1;
    return a.length / 2;
  }

  public static void main(String[] args) {
    int[] a = new int[args.length + 1];

    int m = mid(a);
    if (m >= 0) {
      a[m] = 42;

      if (m >= a.length) {
        System.out.println("unreachable");
      }
    }

    if (a[m] == 123456) System.out.println("unlikely");
  }
}

