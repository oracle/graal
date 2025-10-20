public class SimpleObjectField {
  static class Box { int v; }

  public static void main(String[] args) {
    Box b = new Box();
    b.v = 5;
    int x = b.v + 2;
  }
}