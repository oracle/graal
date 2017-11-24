int main() {
  volatile double a = 0.125;
  if(__builtin_fabs(a) != 0.125) {
    return 1;
  }
  volatile double b = -0.125;
  if(__builtin_fabs(b) != 0.125) {
    return 1;
  }
  volatile double c = 0.;
  if(__builtin_fabs(c) != 0.) {
    return 1;
  }
  volatile double d = -0.;
  if(__builtin_fabs(d) != 0.) {
    return 1;
  }
  return 0;
}
