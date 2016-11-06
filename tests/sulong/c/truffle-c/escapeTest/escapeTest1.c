void bar(int *a) { *a = *a + 1; }

int foo() {
  int a = 2;
  int *pa = &a;
  bar(pa);
  return a;
}

int main() { return foo(); }
