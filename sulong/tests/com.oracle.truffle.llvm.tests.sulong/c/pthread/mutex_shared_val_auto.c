// in the output "thread got the mutex" has always to be followed by "thread increased the value" in the next line
// otherwise some other thread also could aquire the mutex while the other thread did not unlock it yet
// the output / return-value should be equal to NUM_THREADS
// this can be used as automated test, because the output and return-value are always the same (no thread ids in output here)
// functions tested: pthread_create, pthread_exit, pthread_join, pthread_mutex_init, pthread_mutex_lock, pthread_mutex_unlock

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>

#define NUM_THREADS 100

void *inc_var(void *ptr);

pthread_mutex_t *mutex;
int main()
{
	mutex = malloc(sizeof(pthread_mutex_t));
	pthread_mutex_init(mutex, NULL);
	pthread_t threads[NUM_THREADS];
	int val = 0;
	int i;
	for (i = 0; i < NUM_THREADS; i++)
	{
		pthread_create(&threads[i], NULL, inc_var, (void *) &val);
	}
	for (i = 0; i < NUM_THREADS; i++)
	{
		pthread_join(threads[i], NULL);
	}
	printf("shared var is now: %d\n", val);
	return val;
}

void *inc_var(void *ptr)
{
	pthread_mutex_lock(mutex);
	printf("thread got the mutex\n");
	int *int_ptr = ((int *) ptr);
	(*int_ptr)++;
	printf("thread increased the value, value is now: %d\n", *int_ptr);
	pthread_mutex_unlock(mutex);
	pthread_exit(NULL);
}

