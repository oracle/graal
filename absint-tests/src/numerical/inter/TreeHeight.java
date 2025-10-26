public class TreeHeight {
  static class Node { Node left; Node right; }

  static int height(Node n) {
    if (n == null) return 0;
    int hl = height(n.left);
    int hr = height(n.right);
    return 1 + Math.max(hl, hr);
  }

  public static void main(String[] args) {
    Node root = makeTree(); 
    int h = height(root);
  }

  static Node makeTree() { return new Node(); }
}
