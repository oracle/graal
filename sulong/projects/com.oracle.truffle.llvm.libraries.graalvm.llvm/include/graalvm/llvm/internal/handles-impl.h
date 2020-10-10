/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

#ifndef GRAALVM_LLVM_HANDLES_H
#error "Do not include this header directly! Include <graalvm/llvm/handles.h> instead."
#endif

/*
 * DO NOT INCLUDE OR USE THIS HEADER FILE DIRECTLY!
 *
 * Everything in this header file is implementation details, and might change without notice even
 * in minor releases.
 */

__attribute__((noinline)) void *_graalvm_llvm_create_handle(void *managedObject);
__attribute__((noinline)) void *_graalvm_llvm_resolve_handle(void *nativeHandle);
__attribute__((noinline)) void _graalvm_llvm_release_handle(void *nativeHandle);
__attribute__((noinline)) void *_graalvm_llvm_create_deref_handle(void *managedObject);
__attribute__((noinline)) bool _graalvm_llvm_is_handle(void *nativeHandle);
__attribute__((noinline)) bool _graalvm_llvm_points_to_handle_space(void *nativeHandle);

__attribute__((always_inline)) static inline void *create_handle(void *managedObject) {
    return _graalvm_llvm_create_handle(managedObject);
}

__attribute__((always_inline)) static inline void *resolve_handle(void *nativeHandle) {
    return _graalvm_llvm_resolve_handle(nativeHandle);
}

__attribute__((always_inline)) static inline void release_handle(void *nativeHandle) {
    _graalvm_llvm_release_handle(nativeHandle);
}

__attribute__((always_inline)) static inline void *create_deref_handle(void *managedObject) {
    return _graalvm_llvm_create_deref_handle(managedObject);
}

__attribute__((always_inline)) static inline bool is_handle(void *nativeHandle) {
    return _graalvm_llvm_is_handle(nativeHandle);
}

__attribute__((always_inline)) static inline bool points_to_handle_space(void *nativeHandle) {
    return _graalvm_llvm_points_to_handle_space(nativeHandle);
}
