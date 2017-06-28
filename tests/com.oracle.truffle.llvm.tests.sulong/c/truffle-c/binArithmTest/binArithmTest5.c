int main() {
  int a = 5;
  int *pa1 = &a;
  int *pa2 = &a;

  pa1 = pa1 + 2;
  pa1 = pa1 - 1;
  pa1 = pa1 - 1;

  return *pa1 == *pa2;
}
