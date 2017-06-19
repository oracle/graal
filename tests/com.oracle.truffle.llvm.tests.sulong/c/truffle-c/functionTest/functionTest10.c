int doo(int x) { return x + 1; }

int boo(int a, int b) { return doo(a + b); }

int foo(int m, int n) { return boo(m, n); }

int main() {
  int x = 2;
  int y = 3;
  int z = 4;
  return foo(boo(doo(x), y), z);
}
