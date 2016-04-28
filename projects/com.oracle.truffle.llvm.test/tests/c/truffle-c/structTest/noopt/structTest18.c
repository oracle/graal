struct t {
  int a;
  union {
    int a;
    char b[4];
  } uni;
  int c;
};

int a = -4;

int main() {
  struct t test;
  test.a = 3;
  test.c = 5;
  test.uni.b[0] = 1;
  test.uni.b[1] = 1;
  test.uni.b[2] = 1;
  test.uni.b[3] = 1;
  int sum = test.a + test.c + test.uni.a + a;
  printf("vals: %d %d %d %d", test.a, test.c, test.uni.a, a);
  return sum % 256;
}
