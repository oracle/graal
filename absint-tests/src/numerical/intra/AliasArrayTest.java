/*
 Expected invariants:
 - two references a,b alias the same array -> writes through a reflect in reads through b
*/
public class AliasArrayTest {
    private static int res = 0;
    public static void main(String[] args) {
        int[] a = new int[3];
        int[] b = a; // alias
        a[0] = 42;
        res = b[0]; // expect 42
    }
}

