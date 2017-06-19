int foo() {
  int a[5] = { 1, 2, 3, 4, 5 };
  // a[2] = 3;
  return a[2];
}

int compiler() { return foo(); }

int main() {
  compiler();
  compiler();
  return compiler();
}
