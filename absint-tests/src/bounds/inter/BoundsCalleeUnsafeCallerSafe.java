public class BoundsCalleeUnsafeCallerSafe {
  static int uncheckedGet(int[] a, int i) {
    // No internal guard: safety fully depends on caller.
    return a[i];
  }

  public static void main(String[] args) {
    int[] a = new int[5];
    for (int i = 0; i < a.length; i++) a[i] = i;

    int sum = 0;

    // Safe usage: caller guards i before calling.
    for (int i = -2; i < a.length + 2; i++) {
      if (i >= 0 && i < a.length) {
        // On this path, i is definitely in [0, a.length-1], so the call is safe.
        sum += uncheckedGet(a, i);
      }
    }

    // Unsafe usage: caller does not guard the index.
    int bad = uncheckedGet(a, a.length); // always out-of-bounds

    if (sum + bad == 123456) System.out.println("unlikely");
  }
}

