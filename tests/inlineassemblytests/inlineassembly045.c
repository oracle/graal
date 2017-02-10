#include <stdio.h>
struct data {
  int x;
  int y;
};

int main() {
  struct data memory = { .x = 0x12345678, .y = 0x9ABCDEF0 };
  int out;
  __asm__("movl 0x4(%1), %%eax;" : "=a"(out) : "r"(&memory));
  return memory.y == out;
}
