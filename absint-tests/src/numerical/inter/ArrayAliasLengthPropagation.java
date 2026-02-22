public class ArrayAliasLengthPropagation {
    private static int[] makeArray(int n) { return new int[n]; }

    public static void main(String[] args) {
        int[] a = makeArray(3);
        int[] b = a; // alias
        if (b.length == a.length) {
            System.out.println("ALWAYS_TRUE: lengths equal under alias");
        } else {
            System.out.println("UNREACHABLE");
        }
        int i = 2;
        if (i < b.length) {
            a[i] = 99; // safe due to shared length
            System.out.println("ALWAYS_TRUE: i<b.length");
        }
    }
}

