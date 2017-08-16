int main() {
  char out = 0x55;
  __asm__("movl $0x42, %%eax; cmpl $0x42, %%eax; seto %%al" : "=a"(out));
  return out;
}
