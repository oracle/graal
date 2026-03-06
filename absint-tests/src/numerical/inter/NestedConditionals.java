public class NestedConditionals {
  static boolean input() { return true; }

  public static void main(String[] args) {
    int x = 0;
    int y = 0;
    if (input()) {
      x = 1;
      if (input()) y = 2;
      else y = 3;
    }
  }
}
