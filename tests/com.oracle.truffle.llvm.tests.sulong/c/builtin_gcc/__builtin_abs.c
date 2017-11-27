int main() {
  volatile int a = 123456;
  if (__builtin_abs(a) != 123456) {
    return 1;
  }
  volatile int b = -123456;
  if (__builtin_abs(b) != 123456) {
    return 1;
  }
  return 0;
}
