int foo(int *a) { return a[3]; }

int main() {
  int a[4] = { 1, 2, 3, 4 };
  return foo(a);
}
