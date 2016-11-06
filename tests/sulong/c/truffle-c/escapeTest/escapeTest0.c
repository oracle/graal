int foo(int *a) {
  *a += 3;
  return 2;
}

int main() {
  int a = 1;
  return foo(&a) + a;
}
