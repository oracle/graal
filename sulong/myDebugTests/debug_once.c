// code from http://openbook.rheinwerk-verlag.de/linux_unix_programmierung/Kap10-009.htm
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#define MAX_THREADS 3
static pthread_once_t once = PTHREAD_ONCE_INIT;
void thread_once(void) {
   printf("Funktion thread_once() aufgerufen\n");
}
void* thread_func (void* args) {
   printf("Thread %ld wurde gestartet\n", pthread_self());
   printf("once_t has value %d\n", once);
   pthread_once(&once, thread_once);
   printf("once_t has value %d\n", once);
   printf("Thread %ld ist fertig gestartet\n",
      pthread_self());
   pthread_exit(NULL);
}
int main (void) {
  int i;
  pthread_t threads[MAX_THREADS];
  /* Threads erzeugen */
  for (i = 0; i < MAX_THREADS; ++i)
    pthread_create (&(threads[i]), NULL, thread_func, NULL);
  /* Auf die Threads warten  */
  for (i = 0; i < MAX_THREADS; ++i)
    pthread_join (threads[i], NULL);
  return EXIT_SUCCESS;
}

