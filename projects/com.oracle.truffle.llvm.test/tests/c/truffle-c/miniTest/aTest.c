int foo() { return 5; }

int main() {
  int i = 0;
  while (i < 5) {
    int a = foo();
    i++;
  }
  return foo();
}
