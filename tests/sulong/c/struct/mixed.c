struct test {
  double a;
  double b;
  double c;
};

int main() {
  struct test t;
  t.c = 5.7;
  t.b = 3.8;
  t.a = 1;
  return t.a + t.b + t.c;
}
