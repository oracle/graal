int main() {
  volatile float a = __builtin_nanf("");
  if(!__builtin_isnan(a)) {
    return 1;
  }
  volatile float b = __builtin_nansf("");
  if(!__builtin_isnan(b)) {
    return 1;
  }
  volatile double c = __builtin_nan("");
  if(!__builtin_isnan(c)) {
    return 1;
  }
  volatile double d = __builtin_nans("");
  if(!__builtin_isnan(d)) {
    return 1;
  }
  volatile long double e = __builtin_nanl("");
  if(!__builtin_isnan(e)) {
    return 1;
  }
  volatile long double f = __builtin_nansl("");
  if(!__builtin_isnan(f)) {
    return 1;
  }
  volatile float g = 0;
  if(__builtin_isnan(g)) {
    return 1;
  }
  volatile double h = 0;
  if(__builtin_isnan(h)) {
    return 1;
  }
  volatile long double i = 0;
  if(__builtin_isnan(i)) {
    return 1;
  }
  return 0;
}
