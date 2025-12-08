public class Main {

    public static int getMax(int a, int b) {
        if (a < b) {
            return b;
        }
        return a;
    }

    public static void main(String[] args) {
        System.out.println(getMax(1, 2));
        System.out.println(getMax(2, 3));
    }
}
