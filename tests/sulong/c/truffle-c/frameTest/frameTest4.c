int a = 21;

int foo2(int **ppa) {
  int ***pppa = &ppa;
  int b = 21;
  return b + ***pppa;
}

int foo1(int *pa) {
  int **ppa = &pa;
  return foo2(ppa);
}

int main() {
  int *pa = &a;
  return foo1(pa);
}
