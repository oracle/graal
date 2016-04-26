struct test {
  int a;
  int b;
};

int func(struct test t) {
  t.a = 3;
  t.b = 4;
  return t.a + t.b;
}

int main() {
  struct test a = { 1, 2 };
  int result = func(a);
  return result + a.a;
}
