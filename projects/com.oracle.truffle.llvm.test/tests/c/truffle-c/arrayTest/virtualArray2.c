int main() {
  int *b;
  int **c;
  c = &b;
  int a[3];
  int *d = a;
  b = d;
  b[1] = 23;
  return a[1];
}
