// code from http://www.yolinux.com/TUTORIALS/LinuxTutorialPosixThreads.html
// tests conditional wait functions, function1 should increase and write 1-3 an 8-10, function2 4-7
// functions tested: pthread_create, pthread_join, pthread_cond_signal, pthread_cond_wait, pthread_mutex_lock, pthread_mutex_unlock

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>

#define COUNT_DONE  10
#define COUNT_HALT1  3
#define COUNT_HALT2  6

pthread_mutex_t count_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t condition_var = PTHREAD_COND_INITIALIZER;

void *functionCount1();
void *functionCount2();
int  count = 0;

int main()
{
  pthread_t thread1, thread2;
  pthread_create(&thread1, NULL, &functionCount1, NULL);
  pthread_create(&thread2, NULL, &functionCount2, NULL);
  pthread_join(thread1, NULL);
  pthread_join(thread2, NULL);
  printf("Final count is: %d\n", count);
  exit(EXIT_SUCCESS);
}

// write numbers 1-3 and 8-10 as permitted by functionCount2()

void *functionCount1()
{
  for(;;)
  {
    // lock mutex and then wait for signal to relase mutex
    pthread_mutex_lock(&count_mutex);
    // wait while functionCount2() operates on the count var
    // mutex unlocked if condition varialbe in functionCount2() signaled.
    pthread_cond_wait(&condition_var, &count_mutex);
    count++;
    printf("Cur counter value functionCount1: %d\n", count);
    pthread_mutex_unlock(&count_mutex);
    if (count >= COUNT_DONE)
        return(NULL);
  }
}

// write numbers 4-7

void *functionCount2()
{
  for(;;)
  {
    pthread_mutex_lock(&count_mutex);
    if(count < COUNT_HALT1 || count > COUNT_HALT2)
    {
        // condition of if statement has been met. 
        // signal to free waiting thread by freeing the mutex.
        // note: functionCount1() is now permitted to modify "count".
        pthread_cond_signal(&condition_var);
    }
    else
    {
        count++;
        printf("Cur counter value functionCount2: %d\n", count);
    }
    pthread_mutex_unlock(&count_mutex);
    if (count >= COUNT_DONE)
        return(NULL);
  }
}

