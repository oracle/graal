/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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

/**
 * All file types that can be parsed by the GraalVM LLVM runtime, with their magic value for
 * filetype detection and their mime-type.
 */
public enum Magic {
    BC_MAGIC_WORD(0xdec04342L /* 'BC' c0de */, LLVMLanguage.LLVM_BITCODE_MIME_TYPE),
    WRAPPER_MAGIC_WORD(0x0B17C0DEL /* "bitcode" */, LLVMLanguage.LLVM_BITCODE_MIME_TYPE),
    ELF_MAGIC_WORD(0x464C457FL /* '.ELF' */, LLVMLanguage.LLVM_ELF_SHARED_MIME_TYPE),
    MH_MAGIC(0xFEEDFACEL, LLVMLanguage.LLVM_MACHO_MIME_TYPE),
    MH_CIGAM(0xCEFAEDFEL, LLVMLanguage.LLVM_MACHO_MIME_TYPE),
    MH_MAGIC_64(0xFEEDFACFL, LLVMLanguage.LLVM_MACHO_MIME_TYPE),
    MH_CIGAM_64(0xCFFAEDFEL, LLVMLanguage.LLVM_MACHO_MIME_TYPE),
    XAR_MAGIC(0x21726178L, null),
    UNKNOWN(0, null);

    public final long magic;
    public final String mimeType;

    Magic(long magic, String mimeType) {
        this.magic = magic;
        this.mimeType = mimeType;
    }

    private static final Magic[] VALUES = Magic.values();

    public static Magic get(long magic) {
        for (Magic m : VALUES) {
            if (m.magic == magic) {
                return m;
            }
        }
        return UNKNOWN;
    }
}
