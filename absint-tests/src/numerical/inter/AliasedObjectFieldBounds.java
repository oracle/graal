class Buffer {
    int[] data;
    Buffer(int n) { data = new int[n]; }
}

class BufferHolder {
    Buffer buf;
}

public class AliasedObjectFieldBounds {
    private static BufferHolder init(int size) {
        BufferHolder h = new BufferHolder();
        if (size < 0) size = 0;
        if (size > 12) size = 12;
        h.buf = new Buffer(size);
        return h;
    }

    private static BufferHolder alias(BufferHolder h) {
        // create an alias path through another object
        BufferHolder g = new BufferHolder();
        g.buf = h.buf;
        return g;
    }

    private static int selectIndex(BufferHolder h) {
        int len = h.buf.data.length;
        int i = (len * 5) / 7; // not trivially folded to boundary
        if (i < 0) i = 0;
        if (i >= len) i = len - 1;
        return i;
    }

    public static void main(String[] args) {
        BufferHolder h = init(20);
        BufferHolder g = alias(h);
        int i = selectIndex(g);
        if (i >= 0 && i < h.buf.data.length) {
            System.out.println("ALWAYS_TRUE: i in bounds via alias");
            h.buf.data[i] = 1;
        }
        if (h.buf.data.length <= 12) {
            System.out.println("ALWAYS_TRUE: clamped length<=12");
        } else {
            System.out.println("UNREACHABLE");
        }
    }
}

