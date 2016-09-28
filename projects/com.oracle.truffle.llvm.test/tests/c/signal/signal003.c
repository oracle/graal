#include <signal.h>
#include <stdlib.h>
#include <unistd.h>

volatile int glob = 0;

// workaround for the asynchronous sulong signal handler
volatile int sigHandled = 1;
const int MAX_SIG_HANDLED_WAIT = 1000; // ms
void sulong_raise(int signo) {
	sigHandled = 0;
	if(raise(signo) != 0) {
		abort();
	}
	int i = 0;
	while(!sigHandled) {
		if((i++) >= MAX_SIG_HANDLED_WAIT * 10) {
			abort();
		}
		usleep(100); // cause lot's of possibilities for context switches
	}
}

void sig_handler(int signo) {
	if(signo == SIGINT) {
		glob += 10;
	} else if(signo == SIGHUP) {
		glob *= 2;
	} else {
		abort();
	}
	sigHandled = 1;
}


int main() {
	if(signal(SIGINT, sig_handler) != SIG_DFL) {
		abort();
	}

	if(signal(SIGHUP, sig_handler) != SIG_DFL) {
		abort();
	}

	for (int i = 0; i < 2; i++) {
		sulong_raise(SIGINT);
		sulong_raise(SIGHUP);
	}
	return glob;
}
