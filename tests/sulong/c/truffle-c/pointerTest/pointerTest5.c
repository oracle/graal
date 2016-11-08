int foo(int *a) { *a = *a + 1; }

int main() {
  int a;
  int *pa;
  pa = &a;

  *pa = 5;
  foo(pa);
  return a;
}
