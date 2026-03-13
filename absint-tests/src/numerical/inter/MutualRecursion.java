public class MutualRecursion {
  static int even(int n) {
    if (n == 0) return 1;
    else return odd(n - 1);
  }

  static int odd(int n) {
    if (n == 0) return 0;
    else return even(n - 1);
  }

  static int input() { return 5; }

  public static void main(String[] args) {
    int x = input();
    int y = even(x);
  }
}
