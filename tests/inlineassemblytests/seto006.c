int main() {
  char out = 0x55;
  __asm__("movb $0xFF, %%al; cmpb $0x02, %%al; seto %%al" : "=a"(out));
  return out;
}
