public class InterprocLoopContracts {
    private static int contractBound(int n) {
        // clamp into [0, 9]
        if (n < 0) return 0;
        if (n > 9) return 9;
        return n;
    }

    private static int sumPrefix(int[] a, int upto) {
        int u = contractBound(upto);
        int s = 0;
        for (int i = 0; i < u; i++) {
            if (i < a.length) { // always true if u<=a.length
                s += a[i];
            } else {
                // unreachable due to contractBound and a.length==10 below
                s -= 1;
            }
        }
        return s;
    }

    public static void main(String[] args) {
        int[] arr = new int[10];
        for (int i = 0; i < arr.length; i++) arr[i] = i;
        int res = sumPrefix(arr, 100);
        System.out.println(res);
    }
}

