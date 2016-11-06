double foo(double a, double b, double c) {
  double arr[5] = { 1., 2., 3., 4., 5. };
  double d = 4.;
  double *p = &d;
  return a + b + c + arr[4] + arr[0] + *p;
}

int main() {
  double a, b, c;
  a = 2.;
  b = 1.;
  c = 3.;
  double d = 4.;
  double *p = &d;
  int i;
  for (i = 0; i < 10000; i++) {
    *p = foo(a, b, c);
  }
  return (int)*p;
}
