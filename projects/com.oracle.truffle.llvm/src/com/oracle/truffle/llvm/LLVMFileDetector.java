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
package com.oracle.truffle.llvm;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;

public class LLVMFileDetector extends FileTypeDetector {
    private static final long BC_MAGIC_WORD = 0xdec04342L; // 'BC' c0de
    private static final long WRAPPER_MAGIC_WORD = 0x0B17C0DEL;
    private static final long ELF_MAGIC_WORD = 0x464C457FL;

    @Override
    public String probeContentType(Path path) throws IOException {
        long magicWord = readMagicWord(path);
        if (magicWord == BC_MAGIC_WORD || magicWord == WRAPPER_MAGIC_WORD) {
            return Sulong.LLVM_BITCODE_MIME_TYPE;
        } else if (magicWord == ELF_MAGIC_WORD) {
            return Sulong.LLVM_ELF_SHARED_MIME_TYPE;
        }
        return null;
    }

    private static long readMagicWord(Path path) {
        try (InputStream is = new FileInputStream(path.toString())) {
            byte[] buffer = new byte[4];
            if (is.read(buffer) != buffer.length) {
                return 0;
            }
            return Integer.toUnsignedLong(ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder()).getInt());
        } catch (IOException e) {
            return 0;
        }
    }
}
