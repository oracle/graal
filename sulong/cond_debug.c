#include <pthread.h>
#include <stdio.h>

int main() {
	pthread_condattr_t attr;
	pthread_cond_t test;
	
	pthread_my_test(test);	
}

