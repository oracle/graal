public class VariableBoundClamped {
    private static int min(int a, int b) { return a < b ? a : b; }

    public static void main(String[] args) {
        int len = 7;
        int n = 100; // could be large
        int[] arr = new int[len];
        int upto = min(n, arr.length); // clamp
        for (int i = 0; i < upto; i++) {
            if (i >= arr.length) {
                System.out.println("UNREACHABLE: i>=len");
            } else {
                // always true path
                arr[i] = i;
            }
        }
        System.out.println("done");
    }
}

