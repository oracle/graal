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

typedef char v9sb __attribute__((vector_size(9)));
typedef short v8ss __attribute__((vector_size(16)));
typedef int v7si __attribute__((vector_size(28)));
typedef long v6sl __attribute__((vector_size(48)));
typedef float v5flt __attribute__((vector_size(20)));
typedef double v4dbl __attribute__((vector_size(32)));

template <typename T> void foo(T bar) {
}

template <typename T> void doReceive(T toInspect) {
    // call another function to ensure lifetime analysis
    // doesn't kill the value before inspection
    foo(toInspect);
}

__attribute__((constructor)) void test() {
    doReceive((v9sb){ '0', '1', '2', '3', '4', '5', '6', '7', '8' });
    doReceive((v8ss){ 0, 1, 2, 3, 4, 5, 6, 7 });
    doReceive((v7si){ 0, 1, 2, 3, 4, 5, 6 });
    doReceive((v6sl){ 0L, 1L, 2L, 3L, 4L, 5L });
    doReceive((v5flt){ 0.0f, 1.1f, 2.2f, 3.3f, 4.4f });
    doReceive((v4dbl){ 0.0, 1.1, 2.2, 3.3 });
}
