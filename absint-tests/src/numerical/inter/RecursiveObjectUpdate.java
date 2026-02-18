public class RecursiveObjectUpdate {
  static class Node { int val; Node next; }

  static void inc(Node n) {
    if (n == null) return;
    n.val = n.val + 1;
    inc(n.next);
  }

  public static void main(String[] args) {
    Node a = new Node();
    Node b = new Node();
    a.val = 1;
    b.val = 2;
    a.next = b;
    inc(a);
  }
}
