int main() {
  int **a;
  int *b;
  int c = 24;
  b = &c;
  a = &b;
  return **a;
}
