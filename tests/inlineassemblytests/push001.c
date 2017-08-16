int main() {
  unsigned short value = 0;
  __asm__("pushw $0x1234; popw %0" : "=r"(value));
  return value == 0x1234;
}
