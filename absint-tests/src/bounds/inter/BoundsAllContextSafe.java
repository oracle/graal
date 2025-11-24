class BoundsAllContextSafe {
    private static int[] arr = new int[100];

    static void safeStore(int idx, int value) {
        arr[idx] = value;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            safeStore(i, i * 2);
        }
        System.out.println(arr[42]);
    }
}