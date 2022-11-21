/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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

#include <stdarg.h>
#include <stdlib.h>
#include <graalvm/llvm/polyglot.h>

typedef int int_t;
POLYGLOT_DECLARE_TYPE(int_t)
typedef double double_t;
POLYGLOT_DECLARE_TYPE(double_t)

polyglot_typeid get_int_t_typeid() {
    return polyglot_int_t_typeid();
}
polyglot_typeid get_double_t_typeid() {
    return polyglot_double_t_typeid();
}

struct StructA {
    int x;
    int y;
};

POLYGLOT_DECLARE_STRUCT(StructA)

polyglot_typeid get_StructA_typeid() {
    return polyglot_StructA_typeid();
}

void *newStructA(int x, int y) {
    struct StructA *sa = malloc(sizeof(struct StructA));
    sa->x = x;
    sa->y = y;
    return polyglot_from_StructA(sa);
}

int get_next_vaarg(va_list *p_va) {
    return va_arg(*p_va, int);
}

int test_va_list_callback(int (*callback)(va_list *, void *), void *libHandle, ...) {
    va_list argp;

    va_start(argp, libHandle);
    int res = callback(&argp, libHandle);
    va_end(argp);

    return res;
}

int test_va_list_callback4(int (*callback)(va_list *, void *), void *libHandle, int a0, int a1, double a2, void *saNative, void *saManaged) {
    return test_va_list_callback(callback, libHandle, a0, a1, a2, saNative, polyglot_as_StructA(saManaged));
}

int deref_chr_chr_ptr(char **ptr) {
    return (int) **ptr;
}

int test_maybe_va_ptr(int (*callback)(char *)) {
    char chr = 'A';
    char *chr_ptr = &chr;
    /* should be of type i8* which maps to the alias of va_list on some platforms (darwin-aarch64, windows-amd64) */
    char **chr_chr_ptr = alloca(sizeof(char *));
    chr_chr_ptr = &chr_ptr;

    return callback(chr_chr_ptr);
}
