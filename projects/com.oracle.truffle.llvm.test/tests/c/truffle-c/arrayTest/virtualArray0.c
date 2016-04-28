
int main() {
  int i = 0;
  int sum = 0;
  for (i = 5; i < 50; i++) {
    sum += foo();
    sum += foo2();
  }
  return sum / 11;
}

int foo() {
  int a[3];
  a[0] = 1;
  a[1] = 2;
  a[2] = 3;
  return a[0] + a[1] + a[2];
}

int foo2() {
  int *a = calloc(3, sizeof(int));
  a[0] = 1;
  a[1] = 2;
  a[2] = 3;
  return a[0] + a[1] + a[2];
}
