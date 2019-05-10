#include <pthread.h>
#include <stdio.h>
#include <sys/time.h>

pthread_mutex_t fakeMutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t fakeCond = PTHREAD_COND_INITIALIZER;

void mywait(int timeInMs)
{
    struct timespec timeToWait;
    struct timeval now;
    int rt;

    gettimeofday(&now,NULL);


    timeToWait.tv_sec = now.tv_sec+15;
    timeToWait.tv_nsec = (now.tv_usec+1000UL*timeInMs)*1000UL;

    pthread_mutex_lock(&fakeMutex);
    rt = pthread_cond_timedwait(&fakeCond, &fakeMutex, &timeToWait);
    pthread_mutex_unlock(&fakeMutex);
    printf("\nDone\n");
}

void* fun(void* arg)
{
    printf("\nIn thread\n");
    mywait(1000);
}

int main()
{
    pthread_t thread;
    void *ret;

    pthread_create(&thread, NULL, fun, NULL);
    pthread_join(thread,&ret);
}
