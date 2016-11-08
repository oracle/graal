void foo(int ***pppa) {
  pppa += 1;
  pppa = pppa - 1;
  *pppa = *pppa + 1;
  *pppa -= 1;

  **pppa = **pppa + 1;
  **pppa -= 1;

  ***pppa = 23;
}

int main() {
  int a = 1;
  int *pa = &a;
  int **ppa = &pa;
  int ***pppa = &ppa;
  foo(pppa);
  return a;
}
