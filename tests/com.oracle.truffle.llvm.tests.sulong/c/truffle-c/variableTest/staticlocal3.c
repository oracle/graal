int func(int a, int b) {
  static int c = 1;
  static int d = 2;
  c += a;
  d += b;
  return c + d;
}

int main() {
  func(2, 3);
  func(8, 2);
  return func(-3, -2);
}
