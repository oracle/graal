public class RecursiveDecrement {
  static int dec(int n) {
    if (n <= 0) return 0;
    return dec(n - 1);
  }

  static int input() { return 5; }

  public static void main(String[] args) {
    int x = input();
    int y = dec(x);
  }
}
