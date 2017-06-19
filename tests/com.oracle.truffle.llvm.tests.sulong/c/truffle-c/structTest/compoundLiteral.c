struct test {
  int a;
  int b;
};

int main() {
  struct test t;
  t = (struct test) { 4 + 5, 2 };
  return t.a + t.b;
}
