int foo() {
  int a[32];
  int i = 0;
  for (i = 0; i < 32; i++) {
    a[i] = 32;
  }
  return a[31];
}

int main() { return foo(); }
