class Holder {
    int[] arr;
}

public class ObjectFieldAliasingClamp {
    private static Holder makeHolder(int size) {
        Holder h = new Holder();
        if (size < 0) size = 0; // clamp
        h.arr = new int[size];
        return h;
    }

    public static void main(String[] args) {
        Holder h = makeHolder(-5);
        if (h.arr.length >= 0) {
            System.out.println("ALWAYS_TRUE: non-negative length");
        }
        int idx = 0;
        if (idx >= 0 && idx < h.arr.length) {
            System.out.println("UNREACHABLE: idx in empty arr");
        } else {
            System.out.println("ALWAYS_TRUE: idx out of range for empty");
        }
    }
}

