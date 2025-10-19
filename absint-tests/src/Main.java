public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world");
        int x = getFibonacci(10);
        System.out.println(x);
    }


    private static int getFibonacci(int idx) {
        if (idx < 2) {
            return idx;
        }
        return getFibonacci(idx - 1) + getFibonacci(idx - 2);
    }
}
