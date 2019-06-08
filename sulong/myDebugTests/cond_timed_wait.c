// geht irgendwie nicht so...

#include <pthread.h>
#include <stdio.h>
#include <sys/time.h>

pthread_mutex_t fakeMutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t fakeCond = PTHREAD_COND_INITIALIZER;

void mywait(long timeInMs)
{
    struct timespec timeToWait;
    struct timeval now;
    int rt;

    gettimeofday(&now, NULL);

    // timeToWait.tv_sec = now.tv_sec;
    // timeToWait.tv_nsec = (now.tv_usec+timeInMs*1000UL)*1000UL;
    timeToWait.tv_sec = 15;
    timeToWait.tv_nsec = 35;

    pthread_mutex_lock(&fakeMutex);
    rt = pthread_cond_timedwait(&fakeCond, &fakeMutex, &timeToWait);
    pthread_mutex_unlock(&fakeMutex);
    printf("done...\n");
}

void *fun(void *arg)
{
    printf("waiting in thread...\n");
    mywait(500);
}

int main()
{
    pthread_t thread;
    void *ret;

    pthread_create(&thread, NULL, fun, NULL);
    pthread_join(thread, &ret);
}

