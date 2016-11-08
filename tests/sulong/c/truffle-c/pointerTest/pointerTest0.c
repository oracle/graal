int main() {
  int a;
  int *pa;
  int **ppa;
  int ***pppa;

  pppa = &ppa;
  ppa = &pa;
  pa = &a;
  ***pppa = 5;

  return a;
}
