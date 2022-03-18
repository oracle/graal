#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

void *inc(void *ptr);

__thread int j = 0;

int main() {
  pthread_t th1;
  pthread_create(&th1, NULL, inc, NULL);
  pthread_join(th1, NULL);
  pthread_create(&th1, NULL, inc, NULL);
  pthread_join(th1, NULL);
  pthread_create(&th1, NULL, inc, NULL);
  pthread_join(th1, NULL);
  printf("now value is %d\n", j);
  return 0;
}

void *inc(void *ptr) {
    j++;
    printf("thread %d\n", j);
    return NULL;
}