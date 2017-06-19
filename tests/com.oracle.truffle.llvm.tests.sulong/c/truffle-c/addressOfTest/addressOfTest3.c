int main() {
  int i = 3;
  int a[3] = { 1, 2, 3 };
  int *p;

  p = &a[--i];

  return *p;
}
