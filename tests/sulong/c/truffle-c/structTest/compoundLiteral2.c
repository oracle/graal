struct test {
  int a;
  char b[3];
  int c;
};

int main() {
  struct test t;
  t = (struct test) { 4 + 5, { 43, 12, 2 }, 3 };
  return t.a + t.b[0] + t.c + t.b[1] + t.b[2];
}
