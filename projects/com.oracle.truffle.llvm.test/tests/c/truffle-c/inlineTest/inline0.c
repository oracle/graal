int foo(int a, int b, int c) {
  int arr[5] = { 1, 2, 3, 4, 5 };
  int d = 4;
  int *p = &d;
  return a + b + c + arr[4] + arr[0] + *p;
}

int main() {
  int a, b, c;
  a = 2;
  b = 1;
  c = 3;
  int d = 4;
  int *p = &d;
  int i;
  for (i = 0; i < 1234567; i++) {
    *p = foo(a, b, c);
  }
  return *p;
}
