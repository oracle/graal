#include <signal.h>
#include <stdlib.h>
#include <unistd.h>

void sig_handler_1(int signo) { abort(); }

int main() {
  if (signal(-1, sig_handler_1) != SIG_ERR) {
    abort();
  }
  if (signal(SIGKILL, sig_handler_1) != SIG_ERR) {
    abort();
  }

  return 0;
}
