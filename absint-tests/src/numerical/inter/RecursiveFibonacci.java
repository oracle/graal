public class RecursiveFibonacci {
    private static int res = 0;

    public static void main(String[] args) {
        res = getFibonacci(45);
        System.out.println(res);
    }


    private static int getFibonacci(int idx) {
        if (idx < 2) {
            return 1;
        }
        return getFibonacci(idx - 1) + getFibonacci(idx - 2);
    }
}
