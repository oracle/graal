
struct test {
  int a;
  int b;
  int c;
};

int init(struct test *t, int a, int b, int c) {
  t->a = a;
  int *ptr = &t->b;
  *ptr = 10;
  t->c = c;
}

int main() {
  struct test a;
  init(&a, 1, 2, 3);
  return a.a + a.b + a.c;
}
