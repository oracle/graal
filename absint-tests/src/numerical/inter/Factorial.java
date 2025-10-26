public class Factorial {
  static int fact(int n) {
    if (n <= 1) return 1;
    else return n * fact(n - 1);
  }

  static int input() { return 5; }  // unknown integer

  public static void main(String[] args) {
    int x = input();    // unknown integer
    int y = fact(x);
  }
}