void foo(int a[]) { a[2] = 123; }

int main() {
  int a[3] = { 1, 2, 3 };
  a[1] = 123;
  foo(a);
  return a[2];
}
