int main() {
  unsigned long value = 0;
  __asm__("movq $0x123456789ABCDEF, %%rax; pushq %%rax; popq %0" : "=r"(value) : : "%rax");
  return value == 0x123456789ABCDEF;
}
