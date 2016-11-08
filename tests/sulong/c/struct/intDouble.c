struct test {
  int a;
  double b;
};

int main() {
  struct test t;
  t.b = 5.3;
  return t.b;
}
