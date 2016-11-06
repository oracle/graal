int bar(int *a) {
  *(a + 0) = 9;
  *(a + 1) = 8;
  *(a + 2) = 7;
}

int foo() {
  int a[3];
  int b;

  a[0] = 1;
  a[1] = 2;
  a[2] = 3;

  bar(a);

  return a[0] + a[1] + a[2];
}

int main() { return foo(); }
