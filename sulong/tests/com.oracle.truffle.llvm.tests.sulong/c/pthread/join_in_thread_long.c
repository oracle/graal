// thread 0 returns with 0
// thread i joins / waitsfor thread i-1, and returns return-value(thread i-1)+1
// return value should be NUM_THREADS-1
// tests pthread_create, pthread_exit, pthread_join (also in not main-thread)

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>

#define NUM_THREADS 5

void *join_and_wait(void *ptr);
void *wait(void *ptr);

int main()
{	
	pthread_t threads[NUM_THREADS];
	pthread_create(&threads[0], NULL, wait, NULL);	
	int i;		
	for (i = 1; i < NUM_THREADS; i++)
	{
		pthread_create(&threads[i], NULL, join_and_wait, (void *) threads[i-1]);
	}
	long retval;
	pthread_join(threads[NUM_THREADS-1], (void *) &retval);
	printf("now value is %ld\n", retval);
	return retval;
}

void *join_and_wait(void *ptr)
{
	long retval;
	pthread_join((pthread_t) ptr, (void *) &retval);
	printf("thread with retval %ld finished\n", retval);
	sleep(1);
	pthread_exit((void *) retval + 1);
}

void *wait(void *ptr)
{
	sleep(1);
	pthread_exit(0L);
}

