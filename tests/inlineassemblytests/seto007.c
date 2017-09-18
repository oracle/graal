int main() {
  char out = 0x55;
  __asm__("movb $0x02, %%al; cmpb $0xFF, %%al; seto %%al" : "=a"(out));
  return out;
}
