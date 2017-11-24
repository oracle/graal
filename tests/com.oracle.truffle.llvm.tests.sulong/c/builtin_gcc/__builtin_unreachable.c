#include <stdlib.h>

void exit_application() { exit(11); }

int main() {
  exit_application();
  __builtin_unreachable();
}
