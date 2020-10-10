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
#define GRAALVM_LLVM_HANDLES_H

/**
 * \defgroup handles Managed handles API
 * @{
 * @brief Functions for wrapping managed objects from other languages in handles that can be stored
 * in native memory.
 *
 * @file llvm/api/handles.h
 */

#if defined(__cplusplus)
extern "C" {
#endif

#include <stdbool.h>

/**
 * Create a handle to a managed object.
 *
 * Normally, pointers to managed objects can not be stored in native memory. A handle is a special
 * kind of pointer that can be stored in native memory, and that can be resolved back to the
 * managed object using {@link resolve_handle}.
 *
 * Handles created with this function need to be freed manually using {@link release_handle}.
 * The managed object will not be garbage collected as long as a handle to it exists.
 *
 * Calling create_handle on the same object multiple times will return the same handle. Handles are
 * reference counted, the resulting handle will need to be released separately for each time it was
 * created.
 */
static void *create_handle(void *managedObject);

/**
 * Resolve a handle back to the managed pointer.
 *
 * The nativeHandle argument needs to be a handle created with {@link create_handle} or
 * {@link create_deref_handle}. This function will return the managedObject pointer that was passed
 * to the handle creation function.
 */
static void *resolve_handle(void *nativeHandle);

/**
 * Release a handle allocated by {@link create_handle} or {@link create_deref_handle}.
 *
 * Using the handle after it has been reselased is undefined behaviour.
 */
static void release_handle(void *nativeHandle);

/**
 * Create a special handle that can be dereferenced by managed code.
 *
 * This works like {@link create_handle}, but in addition, code running on the GraalVM LLVM runtime
 * can dereference the handle directly without using {@link resolve_handle}.
 *
 * It is possible to pass deref handles down to native code running outside of the GraalVM LLVM
 * runtime, but note that the native code can not dereference these handles directly. Passing them
 * back to the GraalVM LLVM runtime will work though.
 *
 * Using this function comes with a slight performance penalty also for code that does not deal with
 * handles.
 *
 * @see create_handle
 */
static void *create_deref_handle(void *managedObject);

/**
 * Check whether a pointer is a valid handle.
 *
 * @return true for handles created with {@link create_handle} or {@link create_deref_handle},
 *         false for all other values
 */
static bool is_handle(void *nativeHandle);

/**
 * Check whether a pointer points to the special memory area reserved for handles.
 *
 * This function is guaranteed to return true for valid handles. It is also guaranteed to return false
 * for valid (dereferencable) pointers that are not handles.
 *
 * Note that this function can still return true for values that randomly fall in the address range of
 * handles, but are not valid handles themselves.
 *
 * This check is cheaper than {@link is_handle}. If it is known that a value can only be a valid handle
 * or a valid pointer, then this function can be used as a cheaper way to reliably distinguish between
 * those two cases.
 *
 * It can *not* be used to distinguish between handles and random other values, and it can also not
 * be used to distinguish between valid and invalid/released handles. Use {@link is_handle} for that.
 */
static bool points_to_handle_space(void *nativeHandle);

#include <graalvm/llvm/internal/handles-impl.h>

#if defined(__cplusplus)
}
#endif

#endif
