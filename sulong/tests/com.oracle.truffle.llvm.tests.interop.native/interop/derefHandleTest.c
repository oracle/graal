/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
#include <graalvm/llvm/polyglot.h>
#include <graalvm/llvm/handles.h>

typedef int64_t (*fun)(int64_t a, int64_t b);

struct Point {
    int32_t x;
    int32_t y;
    fun identity;
};

POLYGLOT_DECLARE_STRUCT(Point);

void *test_allocate_deref_handle(void *managed) {
    void *arr = create_deref_handle(polyglot_as_Point(managed));
    return arr;
}

int32_t test_read_from_deref_handle(void *managed) {
    struct Point *p = create_deref_handle(polyglot_as_Point(managed));
    int32_t x = p->x;
    int32_t y = p->y;
    return x * x + y * y;
}

void test_write_to_deref_handle(void *managed, int32_t x, int32_t y) {
    struct Point *p = create_deref_handle(polyglot_as_Point(managed));
    p->x = x;
    p->y = y;
}

int64_t test_call_deref_handle(void *managed, int64_t a, int64_t b) {
    fun f = (fun) create_deref_handle(managed);
    return f(a, b);
}

int32_t test_deref_handle_pointer_arith(void *managed) {
    void *p = create_deref_handle(polyglot_as_Point(managed)) + sizeof(int32_t);
    return *(int32_t *) p;
}

int64_t test_call_deref_handle_member(struct Point *p, int64_t a, int64_t b) {
    return p->identity(a, b);
}

int32_t test_add_handle_members(struct Point *p) {
    return p->x + p->y;
}
