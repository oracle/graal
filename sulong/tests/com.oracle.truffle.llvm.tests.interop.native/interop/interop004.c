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
#include <graalvm/llvm/polyglot.h>

int main() {
    void *obj = polyglot_import("foreign");

    void *i = polyglot_get_member(obj, "valueI");
    void *c = polyglot_get_member(obj, "valueB");
    void *l = polyglot_get_member(obj, "valueL");
    void *f = polyglot_get_member(obj, "valueF");
    void *d = polyglot_get_member(obj, "valueD");

    double sum = 0;
    sum += polyglot_as_i32(polyglot_get_array_element(i, 0));
    sum += polyglot_as_i64(polyglot_get_array_element(l, 0));
    sum += polyglot_as_i8(polyglot_get_array_element(c, 0));
    sum += polyglot_as_float(polyglot_get_array_element(f, 0));
    sum += polyglot_as_double(polyglot_get_array_element(d, 0));

    sum += polyglot_as_i32(polyglot_get_array_element(i, 1));
    sum += polyglot_as_i64(polyglot_get_array_element(l, 1));
    sum += polyglot_as_i8(polyglot_get_array_element(c, 1));
    sum += polyglot_as_float(polyglot_get_array_element(f, 1));
    sum += polyglot_as_double(polyglot_get_array_element(d, 1));

    // 73
    return (int) sum;
}
