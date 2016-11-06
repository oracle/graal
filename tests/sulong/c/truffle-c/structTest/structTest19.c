struct l3 {
  union {
    struct {
      int a;
      int b;
    } l1;
    int b;
  } l2;
  int c;
};

int d;

int main() {
  struct l3 test;
  test.l2.l1.a = 3;
  test.l2.l1.b = 4;
  d = -4;
  test.l2.b = 3;
  test.c = 2;
  return test.l2.l1.a + test.l2.l1.b + d + test.l2.b + test.c;
}
