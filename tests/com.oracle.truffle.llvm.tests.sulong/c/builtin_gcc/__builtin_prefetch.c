int main() {
  int a[100], b[100];
  for (int i = 0; i < 100; i++) {
    a[i] = a[i] + b[i];
    __builtin_prefetch (&a[i+1], 1, 1);
    __builtin_prefetch (&b[i+1], 0, 1);
  }
  return 0;
}
