#include<stdint.h>

int main() {
  uint64_t rax, rdx;
   __asm__ ("rdtsc;" : "=a"(rax), "=d"(rdx) : :);
  return rax != 0 || rdx != 0;
}
