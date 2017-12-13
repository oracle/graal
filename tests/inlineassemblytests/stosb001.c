int main() {
  unsigned char buf = 0xCC;
  unsigned long out;
  __asm__("cld\n"
          "lea %1, %%rdi\n"
          "movb $0x42, %%al\n"
          "stosb\n"
          "movq %%rdi, %0"
          : "=r"(out)
          : "m"(buf));
  return (out == ((unsigned long)&buf + 1)) && (buf == 0x42);
}
