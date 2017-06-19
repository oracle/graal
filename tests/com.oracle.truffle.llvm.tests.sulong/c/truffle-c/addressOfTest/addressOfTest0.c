int main() {
  int *p;
  int a[3] = { 1, 2, 3 };

  p = &(*(a + 1));
  return *p;
}
