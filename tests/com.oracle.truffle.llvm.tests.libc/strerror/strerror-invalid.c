#include <string.h>
#include <stdio.h>

int main() {
  char *error1 = strerror(500);
  char *error2 = strerror(501);
  // the glibc uses a static buffer for the error message
  puts(error1);
  puts(error2);
}
