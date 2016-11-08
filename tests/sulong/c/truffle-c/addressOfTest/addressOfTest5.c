int main() {
  int a[3] = { 1, 2, 20 };
  int i = 2;
  return *(&i) + *(&a[2]);
}
