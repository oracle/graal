public class RecursiveFibonacci {

    public static void main(String[] args) {
        int res = getFibonacci(45);
        System.out.println(res);
    }


    private static int getFibonacci(int idx) {
        if (idx < 2) {
            return 1;
        }
        return getFibonacci(idx - 1) + getFibonacci(idx - 2);
    }
}
