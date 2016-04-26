struct test {
  int a;
  int b;
};

int main() {
  struct test t = { 1, 2 };
  struct test d = t;
  t.a = 5;
  t.b = 3;
  return d.a + d.b + t.a + t.b;
}
