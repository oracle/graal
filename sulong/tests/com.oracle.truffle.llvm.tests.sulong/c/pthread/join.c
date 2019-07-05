// joining a thread that increments an int and sleeps 5 seconds
// tests pthread_create, pthread_join

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>

void *inc_a_lot(void *ptr);

int main()
{
	pthread_t th1;
	int val = 0;
	pthread_create(&th1, NULL, inc_a_lot, (void *) &val);
	pthread_join(th1, NULL);
	printf("now value is %d\n", val);
	return val;
}

void *inc_a_lot(void *ptr)
{
	int i = 1;
	while (i <= 5)
	{
		*((int *) ptr) += i++;
		sleep(1);
	}
	pthread_exit(NULL);
}

