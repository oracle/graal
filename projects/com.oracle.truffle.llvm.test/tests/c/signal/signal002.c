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

void sig_handler_1(int signo) {
	abort();
	sigHandled = 1;
}

void sig_handler_2(int signo) {
	if(signo != SIGTERM) {
		abort();
	}
	glob += 10;
	sigHandled = 1;
}

void sig_handler_3(int signo) {
	if(signo != SIGINT) {
		abort();
	}
	glob *= 2;
	sigHandled = 1;
}


int main() {
	if(signal(SIGTERM, sig_handler_1) != SIG_DFL) {
		abort();
	}

	if(signal(SIGTERM, sig_handler_2) != sig_handler_1) {
		abort();
	}

	if(signal(SIGINT, sig_handler_3) != SIG_DFL) {
		abort();
	}

	if(signal(SIGHUP, SIG_IGN) != SIG_DFL) {
		abort();
	}

	sulong_raise(SIGTERM);
	sulong_raise(SIGINT);
	sulong_raise(SIGHUP);

	return glob;
}
