int foo() {
  int i = 0;
  int sum = 0;
  int a[5] = { 1, 2, 3, 4, 5 };
  for (i = 0; i < 5; i++) {
    sum += a[i];
  }
  return sum;
}

int compiler() { return foo(); }

int main() {
  compiler();
  compiler();
  return compiler();
}
