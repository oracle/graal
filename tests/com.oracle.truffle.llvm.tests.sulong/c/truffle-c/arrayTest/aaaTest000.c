int main() {
  int a[1] = { 42 };
  return (*(&a))[0];
}
