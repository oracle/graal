int main() {
  int *a [2];
  {
    a[0] = __builtin_alloca (sizeof(int) * 8);
    a[0][0] = 2;
  }
  {
    a[1] = __builtin_alloca (sizeof(int) * 8);
    a[1][0] = 5;
  }
  return a[0][0] + a[1][0];
}
