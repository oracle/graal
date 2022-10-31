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

extern "C" {
#include <graalvm/llvm/threads.h>
}
#include <stdio.h>

typedef unsigned long long __libcpp_thread_id;
typedef void *__libcpp_thread_t;

#define LIBCPP_EXPORT __declspec(dllexport)

namespace std::__1 {

LIBCPP_EXPORT int __libcpp_thread_create(__libcpp_thread_t *__t, void *(*__func)(void *), void *__arg) {
    __sulong_thread_t sthread;
    int ret = __sulong_thread_create(&sthread, __func, __arg);
    if (ret == 0) {
        *__t = (__libcpp_thread_t) sthread;
    }
    return ret;
}

LIBCPP_EXPORT int __libcpp_thread_join(__libcpp_thread_t *__t) {
    void *ret = __sulong_thread_join((__sulong_thread_t) *__t);
    return 0;
}

LIBCPP_EXPORT bool __libcpp_thread_equal(__libcpp_thread_id __lhs, __libcpp_thread_id __rhs) {
    return __lhs == __rhs;
}

LIBCPP_EXPORT bool __libcpp_thread_less(__libcpp_thread_id __lhs, __libcpp_thread_id __rhs) {
    return __lhs < __rhs;
}

LIBCPP_EXPORT bool __libcpp_thread_is_null(const __libcpp_thread_t *__t) {
    return *__t == 0;
}

LIBCPP_EXPORT __libcpp_thread_id __libcpp_thread_get_current_id() {
    return (__libcpp_thread_id) __sulong_thread_self();
}

LIBCPP_EXPORT __libcpp_thread_id __libcpp_thread_get_id(const __libcpp_thread_t *__t) {
    return (__libcpp_thread_id) *__t;
}

LIBCPP_EXPORT void __libcpp_thread_yield() {
    __sulong_thread_yield();
}
} // namespace std::__1
