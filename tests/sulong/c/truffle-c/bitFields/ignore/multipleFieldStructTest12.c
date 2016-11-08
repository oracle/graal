struct test {
  int a : 5;
  unsigned int b : 17;
  int c : 10;

  int d : 22;
  int e : 12;
  int f : 13;

  int g;
  int i : 22;
  int j : 10;
};

int main() {
  struct test t = { 0, 0, 0, 0, 0, 0, 0, 0, 0 };
  t.a = 16;
  t.d = 0;
  t.g = 13;
  t.j = 12;
  return t.a + t.b + t.c + t.d + t.e + t.f + t.g + t.i + t.j;
}
