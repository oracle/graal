#include <stdio.h>
#include <sys/syscall.h>

int main(void) {
  long error = 0;
  char buf[257];
  __asm__("syscall" : "=a"(error) : "a"(SYS_getcwd), "D"(buf), "S"(sizeof(buf)));
  printf("len: %d\n", error);
  if (error < 0) {
    return 1;
  }
  printf("value: '%s'\n", buf);
  return 0;
}
