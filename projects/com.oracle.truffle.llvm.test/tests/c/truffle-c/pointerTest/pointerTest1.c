int a = 5;
int *pa = &a;
int **ppa = &pa;

int main() {
  *pa += 1;
  return **ppa;
}
