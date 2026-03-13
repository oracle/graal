public class OffByOneUnsafe {
    public static void main(String[] args) {
        int[] arr = new int[5];
        // Intentional off-by-one: i<=arr.length
        for (int i = 0; i <= arr.length; i++) {
            if (i < arr.length) {
                // true for i=0..4
                arr[i] = i;
            } else {
                // false only when i==arr.length
                System.out.println("ALWAYS_TRUE: i==len triggers else");
            }
        }
        System.out.println("done");
    }
}

