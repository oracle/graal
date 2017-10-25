#include <string.h>
#include <stdlib.h>

int main() {
  char *str = "this is a test\n";
  int len = strlen(str);
  if (len != 15) {
    abort();
  }
}
