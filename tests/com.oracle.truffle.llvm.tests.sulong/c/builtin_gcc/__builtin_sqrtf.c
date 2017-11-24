int main() {
  volatile float a = 0;
  if(__builtin_sqrtf(a) != 0) {
    return 1;
  }
  volatile float b = 4;
  if(__builtin_sqrtf(b) != 2) {
    return 1;
  }
  volatile float c = 9;
  if(__builtin_sqrtf(c) != 3) {
    return 1;
  }
  volatile float d = 100;
  if(__builtin_sqrtf(d) != 10) {
    return 1;
  }
  return 0;
}
