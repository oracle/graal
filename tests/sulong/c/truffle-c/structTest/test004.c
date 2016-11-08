struct test {
  int a;
  int b;
};

struct b {
  struct test first;
  struct test second;
};

int main() {
  struct b xxx = { { 2, 3 }, { 4, 6 } };
  return xxx.first.a + xxx.first.b; // + xxx.second.a + xxx.second.b;
}
