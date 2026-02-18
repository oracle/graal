public class FixedBoundSummationSafe {
    public static void main(String[] args) {
        int[] arr = new int[10];
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            arr[i] = i;
        }
        for (int i = 0; i < 10; i++) {
            if (i < arr.length) {
                // always true inside this loop
                sum += arr[i];
            }
        }
        System.out.println("sum=" + sum);
    }
}

