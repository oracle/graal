
struct test {
  int a;
  int b;
  int c;
};

struct test t;

int init(struct test *t, int a, int b, int c) {
  t->a = a;
  int *ptr = &t->b;
  *ptr = 10;
  t->c = c;
}

int main() {
  init(&t, 1, 2, 3);
  return t.a + t.b + t.c;
}
