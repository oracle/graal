class Node { Node next; int val; }

public class FieldCopyAndAliasing {
  public static void main(String[] args) {
    Node a = new Node();
    Node b = new Node();
    a.val = 1;
    b.val = 2;
    a.next = b;
    int x = a.next.val;
  }
}
