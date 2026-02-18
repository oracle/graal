public class RecursiveFactoryBounds {
    private static int clampDepth(int n) {
        if (n < 0) return 0;
        if (n > 6) return 6;
        return n;
    }

    private static int computeSize(int n) {
        // mutual recursion-like chain via decreasing n
        if (n <= 1) return 1;
        return computeSize(n - 2) + 1;
    }

    private static int[] makeArray(int n) {
        int c = clampDepth(n);
        int s = computeSize(c);
        return new int[s];
    }

    private static int chooseIndex(int s) {
        int a = (s * 2) - 1;
        int b = a / 3 + (s % 2);
        return Math.max(0, Math.min(b, s - 1));
    }

    public static void main(String[] args) {
        int[] arr = makeArray(10);
        int idx = chooseIndex(arr.length);
        if (idx >= 0 && idx < arr.length) {
            System.out.println("ALWAYS_TRUE: idx in bounds after recursion");
        } else {
            System.out.println("UNREACHABLE");
        }
        if (arr.length >= 1 && arr.length <= 4) {
            System.out.println("ALWAYS_TRUE: size in [1,4]");
        } else {
            System.out.println("UNREACHABLE");
        }
    }
}

