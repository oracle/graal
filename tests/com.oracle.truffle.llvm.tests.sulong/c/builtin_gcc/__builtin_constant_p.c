volatile int a = 1;

int main() {
  int res = 0;
  res += __builtin_constant_p("asdf") ? 1 : 0;
  res += __builtin_constant_p(12.8) ? 2 : 0;
  res += __builtin_constant_p(0xffff) ? 4 : 0;
  res += __builtin_constant_p(2 + 5) ? 8 : 0;

  res += __builtin_constant_p(a) ? 16 : 0;

  return res;
}
