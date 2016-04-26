int a = 99;
int *pa = &a;

void bar(int **aa) {
  *aa = *aa + 1;
  *aa -= 1;
  **aa = 49;
}

void foo() {
  int *aa;
  aa = pa;
  pa = aa;
  bar(&aa);
}

int main() {
  foo();
  return a;
}
