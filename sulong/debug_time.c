#include <pthread.h>
#include <stdio.h>
#include <sys/time.h>

int main() {
	struct timespec t;
	t.tv_sec = 15;
	t.tv_nsec = 50000;
	t.asdf = 15;
	pthread_my_test(t);
}
