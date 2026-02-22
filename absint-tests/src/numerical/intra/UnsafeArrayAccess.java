public class UnsafeArrayAccess {
    private static int val;
    public static void main(String[] args) {
        int[] a = new int[2];
        a[0] = 0;
        a[1] = 1;
        val = a[1];
    }
}