int main() {
  int **a;
  int *b;
  int c = 33;
  b = &c;
  a = &b;
  int ***d = &a;
  return **a;
}
