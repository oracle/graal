class Range {
    final int lo;
    final int hi;
    Range(int lo, int hi) {
        this.lo = lo;
        this.hi = hi;
    }
}

public class NestedInterprocGuards {
    private static Range mkRange(int base) {
        int lo = base - 2;
        int hi = base + 2;
        if (lo < 0) lo = 0;
        if (hi > 15) hi = 15;
        return new Range(lo, hi);
    }

    private static int choose(Range r, int[] a) {
        int guess = (r.lo + r.hi) / 2;
        int idx = guess;
        if (idx < 0) idx = 0;
        if (idx >= a.length) idx = a.length - 1;
        return idx;
    }

    public static void main(String[] args) {
        int[] a = new int[12];
        Range r = mkRange(20);
        int idx = choose(r, a);
        if (idx >= 0 && idx < a.length) {
            System.out.println("ALWAYS_TRUE: nested guards produce safe idx");
        } else {
            System.out.println("UNREACHABLE");
        }
        // derived branch about r bounds
        if (r.hi <= 15 && r.lo >= 0) {
            System.out.println("ALWAYS_TRUE: range clamped");
        }
    }
}

