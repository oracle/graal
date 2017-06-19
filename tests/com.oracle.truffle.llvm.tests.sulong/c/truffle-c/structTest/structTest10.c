
struct test {
  int a;
};

int main() {
  struct test a;
  int *ptr = &a.a;
  *ptr = 5;
  return a.a;
}
