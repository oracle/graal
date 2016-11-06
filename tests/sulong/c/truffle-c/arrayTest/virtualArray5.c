double foo() {
  double a[3];
  a[0] = 1.0;
  a[1] = 2.0;
  a[2] = 3.0;
  return a[0] + a[1] + a[2];
}

int main() {
  int i = 0;
  double sum = 0;
  for (i = 0; i < 5; i++) {
    sum += foo();
  }
  return sum;
}
