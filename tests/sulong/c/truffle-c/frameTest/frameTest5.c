
int foo2(int a2) {
  int b2 = 21;
  return b2 + a2;
}

int foo1(int a1) {
  int b1 = 2;
  return foo2(a1 + b1);
}

int main() { return foo1(1); }
