struct test {
  double a[3], b, c[3], d;
};

int main() {
  struct test t[] = { {.a = { 0.313, 0.44, 0.38 }, .b = 1.7123e+00, .c = { 13, 23.0, -12.32 }, .d = 32 },
                      {.a = { 1.313, 4.44, -46.38 }, .b = 1.7123e+00, .c = { 13, 21.0, 55.32 }, .d = 3 },
                      {.a = { 12.313, 54, -43 }, .b = 1.7123e+00, .c = { 13, 23.0, -12.32 }, .d = -2.2 } };
  int i;
  double sum = 0;
  for (i = 0; i < 3; i++) {
    sum += t[i].a[0] + t[i].a[1] + t[i].a[2];
    sum += t[i].b;
    sum += t[i].c[0] + t[i].c[1] + t[i].c[2];
    sum += t[i].d;
  }
  return sum;
}
