struct a {
  int a;
};

struct b {
  struct a first;
  struct a second;
};

int main() {
  struct b test;
  test.first.a = 4;
  test.second.a = 8;
  return test.first.a + test.second.a;
}
