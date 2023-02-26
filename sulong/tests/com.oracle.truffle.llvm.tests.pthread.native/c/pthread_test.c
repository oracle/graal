/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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

#define _GNU_SOURCE
#include <stdio.h>
#include <pthread.h>

void *set_named_thread() {

    const pthread_t self = pthread_self();

#if !defined(__APPLE__)
    const int setname_rv_self = pthread_setname_np(self, "self pthread");
#else
    const int setname_rv_self = pthread_setname_np("self pthread");
#endif

    if (setname_rv_self) {
        printf("Could not set pthread name\n");
    }

    char thread_name_self[16];
    const int getname_rv_self = pthread_getname_np(self, thread_name_self, 16);

    if (getname_rv_self) {
        printf("Could not get pthread name\n");
    }

    fprintf(stdout, "My name is '%s'\n", thread_name_self);

    return NULL;
}

int main() {

    pthread_t thread;

    const int create_rv = pthread_create(&(thread), NULL, &set_named_thread, NULL);

    if (create_rv) {
        printf("Could not create thread\n");
        return create_rv;
    }

    const int join_rv = pthread_join(thread, NULL);

    if (join_rv) {
        printf("Could not join thread\n");
        return join_rv;
    }

    return 0;
}
