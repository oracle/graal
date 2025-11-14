// Inter-procedural: simple producer/consumer using last index
public class BoundsProducerConsumer {
  static int consumeLast(int[] a) {
    if (a.length == 0) return 0;
    int i = a.length - 1; // derived safe index
    return a[i];
  }

  static int[] produce(int n) {
    int[] a = new int[n];
    for (int i = 0; i < a.length; i++) a[i] = i + 1;
    return a;
  }

  public static void main(String[] args) {
    int[] a = produce(4);
    int v = consumeLast(a); // safe access
    if (v == -1) System.out.println("impossible");
  }
}
