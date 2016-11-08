struct test {
  int a : 1;
  int : 0;
  int b : 2;
};

int main() {
  struct test t = { 1, 1 };
  return t.a + t.b;
}
