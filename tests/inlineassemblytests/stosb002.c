#include <stdio.h>
#include <string.h>

int main() {
  unsigned char buf[16];
  unsigned long out;
  unsigned int i;

  memset(buf, 0xCC, sizeof(buf));

  __asm__("cld\n"
          "lea %1, %%rdi\n"
          "movb $0x42, %%al\n"
          "movq $10, %%rcx\n"
          "rep stosb\n"
          "movq %%rdi, %0"
          : "=r"(out)
          : "m"(buf[2])
          : "rax", "rcx", "rdi");
  printf("buf:");
  for (i = 0; i < 16; i++)
    printf(" %02X", buf[i]);
  printf("\n");
  return (out == ((unsigned long)&buf[12]));
}
