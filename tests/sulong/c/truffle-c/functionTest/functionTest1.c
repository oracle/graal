static int x = 1;

int foo() {
  if (x == 5) {
    return 123;
  } else {
    x++;
    return foo();
  }
}
int main() { return foo(); }
