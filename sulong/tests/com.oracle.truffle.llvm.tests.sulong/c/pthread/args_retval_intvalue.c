// this testcase tests passing arguments to threads and getting return values
// passing the values directly
// tests pthread_create, pthread_exit, pthread_join

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>

void *add_up_to(void *to);

int main()
{
	pthread_t th1, th2;
	long val1 = 150;
	long val2 = 250;
	pthread_create(&th1, NULL, add_up_to, (void *) val1);
	pthread_create(&th2, NULL, add_up_to, (void *) val2);
	void *ret1, *ret2;
	pthread_join(th1, &ret1);
	pthread_join(th2, &ret2);
	long sum = ((long) ret1) + ((long) ret2);
	printf("thread1 returns sum %ld\n", (long) ret1);
	printf("thread2 returns sum %ld\n", (long) ret2);
	printf("%ld\n", sum);
	if (sum == 42700)
		return 0;
	else
		return 1;
}

void *add_up_to(void *to)
{
	long stop = (long) to;
	long i = 1;
	long *sum = malloc(sizeof(long));
	*sum = 0;
	while (i <= stop)
	{
		*sum += i++;
	}
	pthread_exit((void *) *sum);
}

