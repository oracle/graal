#ifndef _BITS_PTHREADTYPES_H
#define _BITS_PTHREADTYPES_H

typedef unsigned long pthread_t;
typedef void pthread_attr_t;

#endif

#ifndef _PTHREAD_H
#define _PTHREAD_H

#ifndef NULL
#define NULL 0
#endif

extern int pthread_create (pthread_t *thread, const pthread_attr_t *attr, void *(*start_routine) (void *), void *arg);
extern void pthread_exit (void *retval);
extern int pthread_join (pthread_t thread, void **retval);

#endif
