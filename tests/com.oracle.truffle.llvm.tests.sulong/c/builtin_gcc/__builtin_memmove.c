int main() {
  unsigned int a = 0x11223344;
  unsigned int b = 0;
  __builtin_memmove(&a, &b, sizeof(a));
  return a != b;
}
