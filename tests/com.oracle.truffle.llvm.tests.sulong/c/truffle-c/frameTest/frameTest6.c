
int foo2() {
  int b2 = 21;
  return b2;
}

int foo1() {
  int b1 = 2;
  return foo2() + b1;
}

int main() { return foo1() + 1; }
