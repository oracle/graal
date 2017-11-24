int main() {
  unsigned int a[10] = {0xFFFFFFFF};
  __builtin_memset(&a, 0, sizeof(a));
  for(int i = 0; i < 10; i++) {
    if(a[i] != 0) {
      return 1;
    }
  }
  return 0;
}
