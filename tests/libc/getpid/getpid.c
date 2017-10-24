#include <unistd.h>
#include <stdlib.h>

int main() {
  if (getpid() == 0) {
    abort();
  }
}
