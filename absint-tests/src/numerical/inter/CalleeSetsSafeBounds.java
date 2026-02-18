public class CalleeSetsSafeBounds {
    private static int provide() {
        // Return in [2,4]
        int x = 2;
        x += 2; // x = 4
        return x - (x > 3 ? 0 : 1); // 3 or 4; here 4
    }

    public static void main(String[] args) {
        int ret = provide();
        if (ret >= 2 && ret <= 4) {
            System.out.println("ALWAYS_TRUE: 2<=ret<=4");
        } else {
            System.out.println("UNREACHABLE");
        }

        if (ret > 4) {
            System.out.println("UNREACHABLE: ret>4");
        } else {
            System.out.println("ALWAYS_TRUE: ret<=4");
        }
    }
}

