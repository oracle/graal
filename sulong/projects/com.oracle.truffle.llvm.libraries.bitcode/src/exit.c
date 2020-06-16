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
#include <unistd.h>
#include <stdint.h>
#include <stdlib.h>
#include "exit.h"

struct entry {
    struct entry *next;
    void (*func)(void *);
    void *arg;
};

static struct entry head = { NULL, NULL, NULL };

void __sulong_funcs_on_exit() {
    struct entry *entry = head.next;
    while (entry) {
        struct entry *old = entry;
        entry->func(entry->arg);
        entry = entry->next;
        head.next = entry;
        free(old);
    }
    head.next = NULL;
}

void __clear_exit_handlers() {
    struct entry *entry = head.next;
    while (entry) {
        struct entry *old = entry;
        entry = entry->next;
        free(old);
    }
    head.next = NULL;
}

// for now, treat everything running under Sulong as a single dynamic shared object
void *__dso_handle = NULL;

int __cxa_atexit(void (*func)(void *), void *arg, void *dso) {
    struct entry *entry = entry = (struct entry *) malloc(sizeof(struct entry));
    entry->func = func;
    entry->arg = arg;
    entry->next = head.next;
    head.next = entry;
    return 0;
}

static void caller(void *arg) {
    void (*func)(void) = (void *) (void *) arg;
    func();
}

int atexit(void (*func)(void)) {
    return __cxa_atexit(caller, func, NULL);
}

void __sulong_destructor_functions();

void exit(int status) {
    __sulong_funcs_on_exit();
    __sulong_destructor_functions();
    _EXIT(status);
    for (;;) { // this should never be executed
        _EXIT(status);
    }
}

void _exit(int status) {
    _EXIT(status);
    for (;;) { // this should never be executed
        _EXIT(status);
    }
}

void _Exit(int status) {
    _exit(status);
}
