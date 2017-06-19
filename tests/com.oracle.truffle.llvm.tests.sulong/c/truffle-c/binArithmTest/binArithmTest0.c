int main() {
  int b;
  int *a;
  a = &b;
  *a = 5;
  a = a + 1;
  a = a - 1;
  return *a;
}
