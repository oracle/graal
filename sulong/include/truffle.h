/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

/*
 * THIS HEADER FILE IS LEGACY API. IT IS INTENDED FOR INTERNAL USAGE ONLY. DO NOT SHARE OR
 * DEPEND ON THIS INTERFACE. IT MIGHT BE CHANGED OR REMOVED AT ANY TIME. FOR STABLE API,
 * REFER TO HEADERS IN `graalvm/llvm/`.
 */

#ifndef TRUFFLE_H
#define TRUFFLE_H

#if defined(__cplusplus)
extern "C" {
#endif

#include <stdbool.h>
#include <stdlib.h>

#include <graalvm/llvm/handles.h>

// Managed operations
void *truffle_virtual_malloc(size_t size);
void *truffle_managed_malloc(long size);
void *truffle_managed_memcpy(void *destination, const void *source, size_t count);
void *truffle_assign_managed(void *dst, void *managed);

// Managed objects <===> native handles
inline void *truffle_handle_for_managed(void *managedObject) {
    return create_handle(managedObject);
}

inline void *truffle_release_handle(void *nativeHandle) {
    release_handle(nativeHandle);
    return NULL;
}

inline void *truffle_managed_from_handle(void *nativeHandle) {
    return resolve_handle(nativeHandle);
}

inline bool truffle_is_handle_to_managed(void *nativeHandle) {
    return is_handle(nativeHandle);
}

inline void *truffle_deref_handle_for_managed(void *managed) {
    return create_deref_handle(managed);
}

inline bool truffle_cannot_be_handle(void *nativeHandle) {
    return !points_to_handle_space(nativeHandle);
}

// wrapping functions
void *truffle_decorate_function(void *function, void *wrapper);

#if defined(__cplusplus)
}
#endif

#endif
