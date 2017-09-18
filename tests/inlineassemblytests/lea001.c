int main() {
  unsigned long ptr = 0;
  unsigned long out = 0;
  __asm__("lea %1, %0" : "=r"(out) : "m"(ptr));
  return &ptr == (unsigned long*) out;
}
