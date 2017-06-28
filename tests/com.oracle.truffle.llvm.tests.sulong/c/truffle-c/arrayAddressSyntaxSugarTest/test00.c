int main() {
  int a[3] = { 0 };
  if (a == &a[0]) {
    if (a == &a) {
      return 0;
    }
    return 1;
  }
  return 2;
}
