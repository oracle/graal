
struct test {
  int a;
  int b;
  int c;
};

int init(struct test *t, int a, int b, int c) {
  t->a = a;
  t->b = b;
  t->c = c;
}

int main() {
  struct test a;
  init(&a, 1, 2, 3);
  return a.a + a.b + a.c;
}
