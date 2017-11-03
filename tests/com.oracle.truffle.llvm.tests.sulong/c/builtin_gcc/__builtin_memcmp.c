int main() {
  unsigned int a[10] = {0xFFFFFFFF};
  unsigned int b[10] = {0xFFFFFFFF};
  unsigned int c[10] = {0xFFFFFFFE};
  if(__builtin_memcmp(&a, &a, sizeof(a))) {
    return 1;
  }
  if(__builtin_memcmp(&a, &b, sizeof(a))) {
    return 1;
  }
  if(!__builtin_memcmp(&a, &c, sizeof(a))) {
    return 1;
  }
  return 0;
}
