union {
  int a;
} test;

int main() {
  test.a = 4;
  return test.a;
}
