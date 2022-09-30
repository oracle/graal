/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

#include <graalvm/llvm/threads.h>

int __sulong_thread_create(__sulong_thread_t *thread, void *(*start_routine)(void *), void *arg) {
    return sulong_thread_error;
}

void *__sulong_thread_join(__sulong_thread_t thread) {
    return 0;
}

__sulong_thread_t __sulong_thread_self() {
    return -1;
}

int __sulong_thread_setname_np(__sulong_thread_t thread, const char *name) {
    return sulong_thread_error;
}

int __sulong_thread_getname_np(__sulong_thread_t thread, char *name, uint64_t len) {
    return sulong_thread_error;
}

void __sulong_thread_yield() {
}

__sulong_key_t __sulong_thread_key_create(void (*destructor)(void *)) {
    return 0;
}

void __sulong_thread_key_delete(__sulong_key_t key) {
}

void *__sulong_thread_getspecific(__sulong_key_t key) {
    return 0;
}

void __sulong_thread_setspecific(__sulong_key_t key, const void *value) {
}
