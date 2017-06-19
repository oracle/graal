struct test {
  char a;
  int b;
};

struct b {
  struct test first[2];
};

int main() {
  struct b xxx;
  struct b yyy = { { { 'a', 1 }, { 'b', 2 } } };
  xxx = yyy;

  return xxx.first[1].a + xxx.first[1].b;
}
