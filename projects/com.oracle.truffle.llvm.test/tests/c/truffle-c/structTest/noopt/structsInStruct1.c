struct a {
  int a;
  long b;
  char c[3];
};

struct b {
  struct a first;
  struct a second;
};

int main() {
  struct b test;
  test.first.a = 4;
  test.first.b = 3234;
  test.first.c[0] = 0;
  test.first.c[2] = 32;

  test.second.a = 8;
  test.second.b = 3435;
  test.second.c[3] = 43;
  return test.first.a - test.first.b + test.first.c[2] + test.first.c[0] + test.second.a + test.second.b - test.second.c[3];
}
