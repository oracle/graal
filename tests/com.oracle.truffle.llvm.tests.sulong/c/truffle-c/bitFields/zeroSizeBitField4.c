struct test {
  int a : 3;
  int : 0;
  int b[3];
  int c : 2;
};

int main() {
  struct test t = { 1, { 3, 4, 5 }, 1 };
  return t.a + t.b[0] + t.b[1] + t.b[2] + t.c;
}
