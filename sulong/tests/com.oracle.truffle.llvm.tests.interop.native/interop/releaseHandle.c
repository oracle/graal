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
#include <graalvm/llvm/handles.h>

int main() {
    void *object = polyglot_import("object");
    void *handle1 = create_handle(object);
    void *handle2 = create_handle(object);
    void *handle3 = create_deref_handle(object);

    if (!is_handle(handle1)) {
        return 1;
    }
    if (!is_handle(handle2)) {
        return 2;
    }
    if (!is_handle(handle3)) {
        return 3;
    }

    release_handle(handle2);

    if (!is_handle(handle1)) {
        return 4;
    }
    if (!is_handle(handle3)) {
        return 5;
    }

    release_handle(handle1);

    // normal and deref handles are different spaces, so
    // releasing the last "normal" handle will invalidate
    // will invalidate it even if a deref one exists
    if (is_handle(handle1)) {
        return 6;
    }

    release_handle(handle3);

    if (is_handle(handle3)) {
        return 7;
    }

    return 0;
}
