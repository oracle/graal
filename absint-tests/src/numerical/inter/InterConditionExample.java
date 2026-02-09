public class InterConditionExample {

    enum Parity {
        ODD,
        EVEN
    }

    private static Parity getParity(int value) {
        return value % 2 == 0 ? Parity.EVEN : Parity.ODD;
    }

    public static void main(String[] args) {
        Parity parity = getParity(Integer.parseInt(args[0]));
        if (parity != Parity.ODD) {
            System.out.println("it is even");
        } else {
            System.out.println("it is odd");
        }
    }
}
