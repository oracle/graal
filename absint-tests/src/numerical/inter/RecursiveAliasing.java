public class RecursiveAliasing {
  static class Node { Node next; int val; }

  static void set(Node n, int v) {
    if (n == null) return;
    n.val = v;
    set(n.next, v);
  }

  public static void main(String[] args) {
    Node x = new Node();
    Node y = x;
    x.next = new Node();
    set(y, 5);
  }
}
