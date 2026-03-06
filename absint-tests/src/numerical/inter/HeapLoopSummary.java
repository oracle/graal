public class HeapLoopSummary {
  static class Node { Node next; int val; }

  public static void main(String[] args) {
    Node n = head(); // unknown linked list head
    while (n != null) {
      n.val = n.val + 1;
      n = n.next;
    }
  }

  static Node head() { return new Node(); }
}