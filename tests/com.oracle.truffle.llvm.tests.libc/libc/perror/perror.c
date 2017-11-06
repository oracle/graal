#include <stdio.h>
#include <errno.h>

int main() {
  char buffer[100];
  for (int i = 0; i < 10; i++) {
    snprintf(buffer, 100, "errno %d", i);
    errno = i;
    perror(buffer);
  }
}
