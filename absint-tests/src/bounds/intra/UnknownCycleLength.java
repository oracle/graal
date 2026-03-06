public class UnknownCycleLength {
    private static int res = 0;
    public static void main(String[] args) {
        for (String arg : args) {
            res += arg.length();
        }
    }
}