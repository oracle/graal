public class BoundsAlwaysOffByOne {
    public static void main(String[] args) {
        int[] a = new int[5];

        for (int i = 0; i <= a.length; i++) {
            if (i < a.length) {
                a[i] = i;
            } else {
            }
        }
    }
}

