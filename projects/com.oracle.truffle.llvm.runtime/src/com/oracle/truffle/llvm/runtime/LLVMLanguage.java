/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.TruffleLanguage;

public abstract class LLVMLanguage extends TruffleLanguage<LLVMContext> {
    public static final String LLVM_BITCODE_MIME_TYPE = "application/x-llvm-ir-bitcode";
    public static final String LLVM_BITCODE_EXTENSION = "bc";

    /*
     * The Truffle source API does not handle binary data well - it will read binary files in as
     * strings in an unknown encoding. To get around this until it is fixed, we store binary data in
     * base 64 strings when they don't exist as a file which can be read directly.
     */
    public static final String LLVM_BITCODE_BASE64_MIME_TYPE = "application/x-llvm-ir-bitcode-base64";

    // TODO: remove Sulong.SULONG_LIBRARY_MIME_TYPE after GR-5904 is closed.
    public static final String SULONG_LIBRARY_MIME_TYPE = "application/x-sulong-library";

    public static final String LLVM_ELF_SHARED_MIME_TYPE = "application/x-sharedlib";
    public static final String LLVM_ELF_EXEC_MIME_TYPE = "application/x-executable";
    public static final String LLVM_ELF_LINUX_EXTENSION = "so";

    public static final String MAIN_ARGS_KEY = "Sulong Main Args";
    public static final String PARSE_ONLY_KEY = "Parse only";

    public static final String NAME = "llvm";

    public abstract LLVMContext findLLVMContext();

}
