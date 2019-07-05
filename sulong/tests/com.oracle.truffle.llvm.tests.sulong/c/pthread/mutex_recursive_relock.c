// attempts to relock a recursive mutex, should be ok
// tested functions here: pthread_mutexattr_init, pthread_mutexattr_settype(), pthread_mutex_init(), pthread_mutex_lock, pthread_mutex_unlock

#include <pthread.h>
#include <stdio.h>

pthread_mutexattr_t attr;
pthread_mutex_t mutex;
size_t const shared_var = 0;
 
int main() {
  int result;
 
  if ((result = pthread_mutexattr_init(&attr)) != 0) {
    printf("Error - pthread_mutexattr_init() gives return code: %d\n", result);
  }
  if ((result = pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_RECURSIVE)) != 0) {
    printf("Error - pthread_mutexattr_settype() gives return code: %d\n", result);
  }
  if ((result = pthread_mutex_init(&mutex, &attr)) != 0) {
    printf("Error - pthread_mutex_init() gives return code: %d\n", result);
  }
  if ((result = pthread_mutex_lock(&mutex)) != 0) {
    printf("Error - pthread_mutex_lock() gives return code: %d\n", result);
  }

  if ((result = pthread_mutex_lock(&mutex)) != 0) {
    printf("Error - pthread_mutex_lock() second time gives return code: %d\n", result);
  }

  if ((result = pthread_mutex_unlock(&mutex)) != 0) {
    printf("Error - pthread_mutex_unlock() gives return code: %d\n", result);
  }

  return 0;
}

