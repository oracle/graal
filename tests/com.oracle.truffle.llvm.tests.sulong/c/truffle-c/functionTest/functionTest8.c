int foo2(int a, int b, int c, int d) {
  int e = 5;
  return a + c + e;
}

int foo1(int a, int b, int c, int d) {
  int aa = foo2(a, b, c, d);
  int bb = foo2(1, 2, 3, 4);
  int cc = foo2(a, b, c, d);
  int dd = foo2(1, 2, 3, 4);
  int e = foo2(a, b, c, d);
  int f = foo2(1, 2, 3, 4);
  int g = foo2(a, b, c, d);
  int h = foo2(1, 2, 3, 4);
  int i = foo2(a, b, c, d);
  int j = foo2(1, 2, 3, 4);
  int k = foo2(a, b, c, d);

  return aa + bb + cc + dd + e + f + g + h + i + j + k;
}

int main() { return foo1(2, 3, 4, 5); }
