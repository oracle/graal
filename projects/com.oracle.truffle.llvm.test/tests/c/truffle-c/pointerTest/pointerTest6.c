int s = 5;

int foo1(int *a, int *b, int *c, int *d, int *e) { return s + *a + *b + *c + *d + *e; }

int foo2(int *a, int *b, int *c, int *d) {
  int e = 5;
  return foo1(a, b, c, d, &e);
}

int foo3(int *a, int *b, int *c) {
  int d = 5;
  return foo2(a, b, c, &d);
}

int foo4(int *a, int *b) {
  int c = 5;
  return foo3(a, b, &c);
}

int foo5(int *a) {
  int b = 5;
  return foo4(a, &b);
}

int foo6() {
  int a = 5;
  return foo5(&a);
}

int main() { return foo6(); }
