#define _GNU_SOURCE
#include <string.h>
#include <unistd.h>
#include <sys/syscall.h>

int main(void) {
  char *buf = "Hello world!\n";
  int count = strlen(buf);
  syscall(SYS_write, 1, buf, count);
  syscall(SYS_exit, 42);
}
