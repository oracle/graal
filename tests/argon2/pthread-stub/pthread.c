#include <stdio.h>
#include "pthread.h"

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-parameter"

int pthread_create(pthread_t *thread, const pthread_attr_t *attr, void *(*start_routine) (void *), void *arg) {
  static int warn = 1;
  if (warn) {
    fprintf(stderr, "Sulong does not support threads yet, using pthread stub!\n");
    warn = 0;
  }
  start_routine(arg);
  return 0;
}

void pthread_exit(void *retval) {
}

int pthread_join(pthread_t thread, void **retval) {
  return 0;
}

#pragma clang diagnostic pop
