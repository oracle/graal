#include <string.h>
#include <sys/syscall.h>

int main(void) {
  int fd = 1;
  char *buf = "Hello world!\n";
  int count = strlen(buf);
  __asm__("syscall" : : "a"(SYS_write), "D"(fd), "S"(buf), "d"(count));
  return 0;
}
