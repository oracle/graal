public class RecursiveListSum {
  static class Node { int val; Node next; }

  static int sum(Node n) {
    if (n == null) return 0;
    return n.val + sum(n.next);
  }

  public static void main(String[] args) {
    Node a = new Node();
    Node b = new Node();
    a.val = 1;
    b.val = 2;
    a.next = b;
    b.next = null;
    int s = sum(a);
  }
}
