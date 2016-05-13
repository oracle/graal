struct test {
  int a[3];
  char b[2];
  long c;
};

int sum(struct test *t) {
  int sum = 0;
  sum += t->a[0] + t->a[1] + t->a[2];
  sum += t->b[0] + t->b[1];
  sum += t->c;
  return sum;
}

int main() {
  struct test t1 = { .a = { 1, 2, 3 }, .b = { 'a', 'c' }, .c = -1 };

  struct test t2 = t1;
  t2.b[0] = 32;
  t2.a[0] = 12;
  t2.a[2] = 1;
  return (sum(&t1) + sum(&t2)) % 256;
}
