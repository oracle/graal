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

#ifndef LLVM_API_TOOLCHAIN_H
#define LLVM_API_TOOLCHAIN_H

/**
 * \defgroup toolchain LLVM Toolchain API
 * @{
 * @brief Access to the {@link com::oracle::truffle::llvm::api::Toolchain Toolchain API} from LLVM.
 *
 * All functions accept C strings or {@link polyglot_is_string polyglot strings} and return {@link polyglot polyglot values}.
 *
 * <h3>Example</h3>
 *
 * @snippet tests/com.oracle.truffle.llvm.tests.interop.native/interop/polyglotToolchain.c toolchain.h usage example
 *
 * @see <a href="https://github.com/oracle/graal/blob/master/sulong/projects/com.oracle.truffle.llvm.api/src/com/oracle/truffle/llvm/api/Toolchain.java">Toolchain.java</a>
 *
 * @file llvm/api/toolchain.h
 */

#if defined(__cplusplus)
extern "C" {
#endif

/**
 * Gets the path to the executable for a given tool.
 *
 * @param name The name of the requested tool.
 * @return The path to the tool as a {@link polyglot_is_string polyglot string} or \c NULL if the tool is not supported.
 */
void *toolchain_api_tool(const void *name);

/**
 * Returns a list of directories for a given path name.
 *
 * @param name The name of the requested path.
 * @return A {@link polyglot_has_array_elements polyglot array} of paths as {@link polyglot_is_string polyglot strings} or \c NULL if the path is not supported.
 */
void *toolchain_api_paths(const void *name);

/**
 * Returns an identifier for the toolchain.
 *
 * @return The identifier of the toolchain as a polyglot string.
 */
void *toolchain_api_identifier(void);

/** @} */

#if defined(__cplusplus)
}
#endif

#endif
