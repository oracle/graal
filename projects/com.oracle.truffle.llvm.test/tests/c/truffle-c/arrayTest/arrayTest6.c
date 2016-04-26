int foo(int a[]) { return a[2]; }

int main() {
  int a[3] = { 1, 2, 3 };
  return foo(a);
}
