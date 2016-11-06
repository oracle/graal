struct test {
  char a;
  int b;
};

struct test2 {
  char a;
  int b[2];
};

struct b {
  struct test first[2];
  int second[3];
  struct test2 third[2];
  struct test forth;
};

int main() {
  struct b xxx;
  struct b yyy = { { { 'a', 1 }, { 'b', 2 } }, { 3, 4, 5 }, { { 'c', { 6, 7 } }, { 'd', { 8, 9 } } }, { 'e', 10 } };
  xxx = yyy;

  return (xxx.first[0].a + xxx.first[0].b + xxx.first[1].a + xxx.first[1].b + xxx.second[0] + xxx.second[1] + xxx.second[2] + xxx.third[0].a +
          xxx.third[0].b[0] + xxx.third[0].b[1] + xxx.third[1].a + xxx.third[1].b[0] + xxx.third[1].b[1] + xxx.forth.a + xxx.forth.b) %
         255;
}
