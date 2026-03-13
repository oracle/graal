interface SizeProvider {
    int size();
}

class FixedSize implements SizeProvider {
    private final int n;
    FixedSize(int n) { this.n = n; }
    public int size() { return n; }
}

class ClampedSize implements SizeProvider {
    private final int n;
    ClampedSize(int n) { this.n = n; }
    public int size() {
        int s = n;
        if (s < 0) s = 0;
        if (s > 10) s = 10;
        return s;
    }
}

public class InterprocContractsVirtualDispatch {
    private static int[] make(SizeProvider sp) {
        return new int[sp.size()];
    }

    private static int safeIndex(int guess, int len) {
        // non-trivial arithmetic to avoid folding
        int g = (guess * 3) - (guess / 2);
        if (g < 0) return 0;
        if (g >= len) return len - 1;
        return g;
    }

    public static void main(String[] args) {
        SizeProvider sp = args.length > 0 ? new ClampedSize(15) : new FixedSize(8);
        int[] arr = make(sp);
        int idx = safeIndex(arr.length - 1, arr.length);
        if (idx >= 0 && idx < arr.length) {
            // should be always true regardless of dispatch due to clamping
            arr[idx] = 1;
            System.out.println("ALWAYS_TRUE: idx within length via dispatch and clamp");
        } else {
            System.out.println("UNREACHABLE");
        }
        // demonstrate branch on upper bound of size
        if (arr.length > 10) {
            System.out.println("UNREACHABLE: arr.length>10");
        } else {
            System.out.println("ALWAYS_TRUE: arr.length<=10");
        }
    }
}

