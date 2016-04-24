struct test {
  int a;
  long b;
  int c[3];
};

int main() {
  struct test t = { 1, 2, { 4, 8, 16 } };
  return t.a + t.b + t.c[0] + t.c[1] + t.c[2];
}
