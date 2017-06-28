int c;

int foo() {
  static int i = 0;
  int a = 2;
  if (c == 0) {
    return a + i;
  } else {
    i++;
    c--;
    return foo();
  }
}

int main() {
  c = 100;
  return foo();
}
