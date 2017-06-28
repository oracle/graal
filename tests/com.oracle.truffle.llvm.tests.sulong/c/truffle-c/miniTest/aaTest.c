int main() {
  int a[3] = { 2, 3, 4 };
  a[0] = a[0] * 2 + a[1] - a[2];
  return a[0];
}
