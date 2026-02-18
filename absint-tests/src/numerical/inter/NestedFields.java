public class NestedFields {
  static class Node { Node next; int val; }

  public static void main(String[] args) {
    Node n = new Node();
    n.val = 0;
    n.next = new Node();
    n.next.val = 42;
    int a = n.next.val;
  }
}