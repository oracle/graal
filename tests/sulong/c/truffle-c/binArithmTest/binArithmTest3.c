int main() {
  int *pa;
  int a;
  pa = &a;
  a = 5;
  int b = 3;
  a = *pa + b;
  return a;
}
