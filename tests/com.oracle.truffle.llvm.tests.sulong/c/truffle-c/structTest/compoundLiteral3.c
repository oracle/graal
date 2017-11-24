struct test {
  int a;
  int b;
};

int main() {
  struct test t = (struct test){ 9, 2 };
  return t.a + t.b;
}
