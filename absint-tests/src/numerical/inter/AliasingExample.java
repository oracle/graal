class Box { int v; }

public class AliasingExample {
  public static void main(String[] args) {
    Box a = new Box();
    Box b = a;
    a.v = 10;
    int x = b.v;
  }
}