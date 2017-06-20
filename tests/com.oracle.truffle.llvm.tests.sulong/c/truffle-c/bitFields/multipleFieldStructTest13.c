struct test {
  int a : 1;
  int b : 1;
  int c : 1;

  char d : 1;
  int e : 1;
  char f : 1;

  long g : 1;
  unsigned long i : 1;
  long h : 1;
};

int main() {
  struct test t = { 0, 0, 0, 0, 0, 0, 0, 0, 0 };
  t.b = 1;
  t.e = 1;
  t.i = 1;
  return 10 + t.a + t.b + t.c + t.d + t.e + t.f + t.g + t.h + t.i;
}
