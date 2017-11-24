int main() {
  int res = 0;
  for(int i = 0; i < 10; i++) {
    if(__builtin_expect(i % 2, 0)) {
      res += i;
    }
  }
  return res;
}
