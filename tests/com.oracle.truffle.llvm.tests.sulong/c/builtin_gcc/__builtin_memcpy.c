int main() {
  unsigned int a = 0x11223344;
  unsigned int b = 0;;
  __builtin_memcpy(&a, &b, sizeof(a));
  return a != b;
}
