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

#ifndef GRAALVM_LLVM_POLYGLOT_BUFFER_H
#define GRAALVM_LLVM_POLYGLOT_BUFFER_H

#include <graalvm/llvm/polyglot.h>

/**
 * Convert the provided memory pointer into a buffer of length `length`.
 *
 * @see org::graalvm::polyglot::Value::hasBufferElements
 *
 */
polyglot_value polyglot_from_buffer(void *buffer, uint64_t length);

/**
 * Convert the provided memory pointer into a read-only buffer of length
 * `length`.
 *
 * @see org::graalvm::polyglot::Value::hasBufferElements
 * @see org::graalvm::polyglot::Value::isBufferWritable
 */
polyglot_value polyglot_from_const_buffer(const void *buffer, uint64_t length);

/**
 * Check whether a polyglot value is a buffer.
 *
 * Buffer objects may be converted into pointer objects and written to directly.
 *
 * \code
 * if (polyglot_has_buffer_elements(buffer)
 *     && polyglot_is_buffer_writable(buffer)
 *     && polyglot.get_buffer_size(buffer) > 8) {
 *   int32_t *pBuffer = (int32_t*)buffer;
 *   pBuffer[1] = 42;
 * }
 * \endcode
 *
 * Returns false for pointers that do not point to a polyglot value (see
 * {@link polyglot_is_value}).
 *
 * @see org::graalvm::polyglot::Value::hasBufferElements
 */
bool polyglot_has_buffer_elements(polyglot_value buffer);

/**
 * Check whether a polyglot value is a modifiable buffer.
 *
 * This function should only be called on buffer objects.
 *
 * @see org::graalvm::polyglot::Value::hasBufferElements
 */
bool polyglot_is_buffer_writable(polyglot_value buffer);

/**
 * Get the length of a polyglot buffer.
 *
 * This function should only be called on buffer objects.
 *
 * @see org::graalvm::polyglot::Value::getBufferSize
 */
uint64_t polyglot_get_buffer_size(polyglot_value buffer);

#endif
