/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include <errno.h>
#include <sys/types.h>
#include <unistd.h>
#include <pthread.h>

#include "unsupported.h"

int pthread_attr_getdetachstate(const pthread_attr_t *attr, int *detachstate) {
  ERR_UNSUPPORTED(pthread_attr_getdetachstate);
}
int pthread_attr_getguardsize(const pthread_attr_t *attr, size_t *guardsize) {
  ERR_UNSUPPORTED(pthread_attr_getguardsize);
}
int pthread_attr_getinheritsched(const pthread_attr_t *restrict attr, int *restrict inheritsched) {
  ERR_UNSUPPORTED(pthread_attr_getinheritsched);
}
int pthread_attr_getschedparam(const pthread_attr_t *restrict attr, struct sched_param *restrict param) {
  ERR_UNSUPPORTED(pthread_attr_getschedparam);
}
int pthread_attr_getschedpolicy(const pthread_attr_t *restrict attr, int *restrict policy) {
  ERR_UNSUPPORTED(pthread_attr_getschedpolicy);
}
int pthread_attr_getscope(const pthread_attr_t *restrict attr, int *restrict contentionscope) {
  ERR_UNSUPPORTED(pthread_attr_getscope);
}
int pthread_attr_getstackaddr(const pthread_attr_t *attr, void **stackaddr) {
  ERR_UNSUPPORTED(pthread_attr_getstackaddr);
}
int pthread_attr_getstacksize(const pthread_attr_t *restrict attr, size_t *restrict stacksize) {
  ERR_UNSUPPORTED(pthread_attr_getstacksize);
}
int pthread_attr_setdetachstate(pthread_attr_t *attr, int detachstate) {
  ERR_UNSUPPORTED(pthread_attr_setdetachstate);
}
int pthread_attr_setguardsize(pthread_attr_t *attr, size_t guardsize) {
  ERR_UNSUPPORTED(pthread_attr_setguardsize);
}
int pthread_attr_setinheritsched(pthread_attr_t *attr, int inheritsched) {
  ERR_UNSUPPORTED(pthread_attr_setinheritsched);
}
int pthread_attr_setschedparam(pthread_attr_t *restrict attr, const struct sched_param *restrict param) {
  ERR_UNSUPPORTED(pthread_attr_setschedparam);
}
int pthread_attr_setschedpolicy(pthread_attr_t *attr, int policy) {
  ERR_UNSUPPORTED(pthread_attr_setschedpolicy);
}
int pthread_attr_setscope(pthread_attr_t *attr, int contentionscope) {
  ERR_UNSUPPORTED(pthread_attr_setscope);
}
int pthread_attr_setstackaddr(pthread_attr_t *attr, void *stackaddr) {
  ERR_UNSUPPORTED(pthread_attr_setstackaddr);
}
int pthread_attr_setstacksize(pthread_attr_t *attr, size_t stacksize) {
  ERR_UNSUPPORTED(pthread_attr_setstacksize);
}
int pthread_cancel(pthread_t thread) {
  ERR_UNSUPPORTED(pthread_cancel);
}
// void  pthread_cleanup_push(void*, void *);
// void  pthread_cleanup_pop(int);
int pthread_condattr_destroy(pthread_condattr_t *attr) {
  ERR_UNSUPPORTED(pthread_condattr_destroy);
}
int pthread_condattr_getpshared(const pthread_condattr_t *restrict attr, int *restrict pshared) {
  ERR_UNSUPPORTED(pthread_condattr_getpshared);
}
int pthread_condattr_init(pthread_condattr_t *attr) {
  ERR_UNSUPPORTED(pthread_condattr_init);
}
int pthread_condattr_setpshared(pthread_condattr_t *attr, int pshared) {
  ERR_UNSUPPORTED(pthread_condattr_setpshared);
}
int pthread_detach(pthread_t thread) {
  ERR_UNSUPPORTED(pthread_detach);
}
int pthread_getconcurrency(void) {
  ERR_UNSUPPORTED(pthread_getconcurrency);
}
int pthread_getschedparam(pthread_t thread, int *restrict policy, struct sched_param *restrict param) {
  ERR_UNSUPPORTED(pthread_getschedparam);
}
int pthread_rwlockattr_destroy(pthread_rwlockattr_t *attr) {
  ERR_UNSUPPORTED(pthread_rwlockattr_destroy);
}
int pthread_rwlockattr_getpshared(const pthread_rwlockattr_t *restrict attr, int *restrict pshared) {
  ERR_UNSUPPORTED(pthread_rwlockattr_getpshared);
}
int pthread_rwlockattr_init(pthread_rwlockattr_t *attr) {
  ERR_UNSUPPORTED(pthread_rwlockattr_init);
}
int pthread_rwlockattr_setpshared(pthread_rwlockattr_t *attr, int pshared) {
  ERR_UNSUPPORTED(pthread_rwlockattr_setpshared);
}
//pthread_t pthread_self(void) {
//  ERR_UNSUPPORTED(pthread_self);
//}
int pthread_setcancelstate(int state, int *oldstate) {
  ERR_UNSUPPORTED(pthread_setcancelstate);
}
int pthread_setcanceltype(int type, int *oldtype) {
  ERR_UNSUPPORTED(pthread_setcanceltype);
}
int pthread_setconcurrency(int new_level) {
  ERR_UNSUPPORTED(pthread_setconcurrency);
}
int pthread_setschedparam(pthread_t thread, int policy, const struct sched_param *param) {
  ERR_UNSUPPORTED(pthread_setschedparam);
}
void pthread_testcancel(void) {
  // do nothing - this is fine as long as no other pthread methods are supported
}
