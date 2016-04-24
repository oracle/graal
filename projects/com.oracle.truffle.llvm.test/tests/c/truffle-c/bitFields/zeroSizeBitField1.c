struct test {
  int a : 1;
  int : 0;
  int b : 2;
};

int main() {
  struct test t;
  t.a = 1;
  t.b = 1;
  return t.a + t.b;
}
