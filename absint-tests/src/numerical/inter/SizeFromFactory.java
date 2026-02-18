public class SizeFromFactory {
    private static int[] mk(int size) {
        return new int[size];
    }

    public static void main(String[] args) {
        int[] a = mk(5);
        int i = 3;
        if (i < a.length && i >= 0) {
            System.out.println("ALWAYS_TRUE: 0<=i<a.length");
            a[i] = 1; // safe
        } else {
            System.out.println("UNREACHABLE");
        }
        if (i == 5) {
            System.out.println("UNREACHABLE: i==5");
        } else {
            System.out.println("ALWAYS_TRUE: i!=5");
        }
    }
}

