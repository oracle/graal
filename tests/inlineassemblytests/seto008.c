int main() {
  char out = 0x55;
  __asm__("movb $0x7F, %%al; cmpb $0x7F, %%al; seto %%al" : "=a"(out));
  return out;
}
