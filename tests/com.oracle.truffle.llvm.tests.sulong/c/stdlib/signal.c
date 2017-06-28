#include <signal.h>

void sig_handler(int signo) {}

int main(void) {
  signal(SIGINT, sig_handler);
  return 0;
}
