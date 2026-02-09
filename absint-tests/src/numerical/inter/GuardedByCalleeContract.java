public class GuardedByCalleeContract {
    private static int clampNonNegative(int x) {
        return x < 0 ? 0 : x;
    }

    public static void main(String[] args) {
        int a = clampNonNegative(-5);
        int b = clampNonNegative(10);
        if (a < 0) {
            System.out.println("UNREACHABLE: a<0");
        } else {
            System.out.println("ALWAYS_TRUE: a>=0");
        }
        if (b < 0) {
            System.out.println("UNREACHABLE: b<0");
        } else {
            System.out.println("ALWAYS_TRUE: b>=0");
        }
    }
}

