void swapArgs(int *a, int *b) {
  int tmp;
  tmp = *a;
  *a = *b;
  *b = tmp;
}

int main() {
  int m = 13, n = 44;
  swapArgs(&m, &n);
  return m;
}
