int main() {
  char out = 0x55;
  __asm__("movb $0xFE, %%al; cmpb $0xFF, %%al; seto %%al" : "=a"(out));
  return out;
}
