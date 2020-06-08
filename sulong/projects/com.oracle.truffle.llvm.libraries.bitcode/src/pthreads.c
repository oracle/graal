/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
#include <pthread.h>

/*
 * On different platforms, pthread_t and pthread_key_t might be different types
 * (e.g. on Linux they are long/int, on Darwin they are pointer/long). We do an
 * indirection here to abstract away the difference. On GraalVM, both are just
 * implemented as IDs.
 */
typedef long __sulong_thread_t;
typedef int __sulong_key_t;

int __sulong_thread_create(__sulong_thread_t *thread, void *(*start_routine)(void *), void *arg);
void *__sulong_thread_join(long thread);
__sulong_thread_t __sulong_thread_self();

__sulong_key_t __sulong_thread_key_create(void (*destructor)(void *));
void __sulong_thread_key_delete(__sulong_key_t key);
void *__sulong_thread_getspecific(__sulong_key_t key);
void __sulong_thread_setspecific(__sulong_key_t key, const void *value);

int pthread_create(pthread_t *thread, const pthread_attr_t *attr, void *(*start_routine)(void *), void *arg) {
    __sulong_thread_t sthread;
    int ret = __sulong_thread_create(&sthread, start_routine, arg);
    if (ret == 0) {
        *thread = (pthread_t) sthread;
    }
    return ret;
}

#if !defined(pthread_equal)
// some libcs have pthread_equal as a macro that simply does ==
// others have it as an actual function
int pthread_equal(pthread_t thread1, pthread_t thread2) {
    return thread1 == thread2;
}
#endif

void pthread_exit(void *); // intrinsic

int pthread_join(pthread_t thread, void **retval) {
    void *ret = __sulong_thread_join((__sulong_thread_t) thread);
    if (retval) {
        *retval = ret;
    }
    return 0;
}

pthread_t pthread_self() {
    return (pthread_t) __sulong_thread_self();
}

int pthread_key_create(pthread_key_t *key, void (*destructor)(void *)) {
    *key = (pthread_key_t) __sulong_thread_key_create(destructor);
    return 0;
}

int pthread_key_delete(pthread_key_t key) {
    __sulong_thread_key_delete((__sulong_key_t) key);
    return 0;
}

void *pthread_getspecific(pthread_key_t key) {
    return __sulong_thread_getspecific((__sulong_key_t) key);
}

int pthread_setspecific(pthread_key_t key, const void *value) {
    __sulong_thread_setspecific((__sulong_key_t) key, value);
    return 0;
}
