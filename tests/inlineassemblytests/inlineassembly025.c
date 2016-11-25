#include<stdint.h>

int main() {
  uint64_t rax, rdx;
   __asm__ ("rdtsc;" : "=a"(rax), "=d"(rdx) : :);
  return 0;
}
