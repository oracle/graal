int main() {
  unsigned long arg1 = 0xF174123492FAC1FFL;
  unsigned long arg2 = 0xFC1935FB3D5EA761L;
  unsigned long mulLo = 0;
  unsigned long mulHi = 0;
  __asm__("mulq %%rbx;" : "=a"(mulLo), "=d"(mulHi) : "a"(arg1), "b"(arg2));
  return (mulLo == 0x671BEA1F4432DA9FL) && (mulHi == 0xEDC6092B8645DC41L);
}
