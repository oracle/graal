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

  return yyy.first[1].a + yyy.first[1].b;
}
