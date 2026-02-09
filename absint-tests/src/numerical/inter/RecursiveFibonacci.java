class RecursiveFibonacci {
    static void main(String[] args) {
        System.out.println(getFibonacci(40));
    }
    static int getFibonacci(int idx) {
        if (idx < 2) {
            return 1;
        }
        return getFibonacci(idx - 1) + getFibonacci(idx - 2);
    }
}
