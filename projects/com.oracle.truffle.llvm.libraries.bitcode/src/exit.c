/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
#include <stdint.h>
#include <stdlib.h>
#include "syscall.h"

struct entry {
  struct entry *next;
  void (*func)(void *);
  void *arg;
};

static struct entry head = { NULL, NULL, NULL };

static void __funcs_on_exit() {
  struct entry *entry = head.next;
  while (entry) {
    struct entry *old = entry;
    entry->func(entry->arg);
    entry = entry->next;
    free(old);
  }
  head.next = NULL;
}

__attribute__((weak)) int __cxa_atexit(void (*func)(void *), void *arg, void *dso) {
  struct entry *entry = entry = (struct entry *)malloc(sizeof(struct entry));
  entry->func = func;
  entry->arg = arg;
  entry->next = head.next;
  head.next = entry;
  return 0;
}

static void caller(void *arg) {
  void (*func)(void) = (void *)(void *)arg;
  func();
}

__attribute__((weak)) int atexit(void (*func)(void)) { return __cxa_atexit(caller, func, NULL); }

__attribute__((weak)) void exit(int status) {
  int64_t result;
  __funcs_on_exit();
  __SYSCALL_1(result, SYS_exit, status);
  for (;;) {
    __SYSCALL_1(result, SYS_exit, status);
  }
}
