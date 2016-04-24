struct test {
  int a;
  long b;
  int c[3];
};

int func(struct test t) {
  int sum = 0;
  sum += t.a;
  sum += t.b;
  sum += t.c[0];
  sum += t.c[1];
  sum += t.c[2];
  return sum;
}

int main() {
  struct test t = { 1, 2, { 4, 8, 16 } };
  struct test t2 = { -1, -2, { -4, -8, -16 } };
  t = t2;
  t2.b = 0;
  return func(t) + func(t2) + 100;
}
