int main() {
  int i = 0;
  int sum = 0;
  for (i = 5; i < 50; i++) {
    sum += foo();
  }
  return sum / 10;
}

int foo() {
  int a[3] = { 1, 2, 3 };
  return a[0] + a[1] + a[2];
}
