int main() {
  char out = 0x55;
  __asm__("movb $0xFF, %%al; cmpb $0xFE, %%al; seto %%al" : "=a"(out));
  return out;
}
