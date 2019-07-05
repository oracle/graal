// tests pthread_join on non init thread
// due to spec undefined behaviour, but should not crash anyway i guess

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>

int main()
{	
	pthread_t noninit_thread;	
	int result = pthread_join(noninit_thread, NULL);
	printf("return val is %d\n", result);
	return result;
}

