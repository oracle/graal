/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.coff;

import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.truffle.llvm.parser.filereader.ObjectFileReader;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;

/**
 * Windows Portable Executable File.
 *
 * A PE file starts with a MS DOS header which includes a pointer the PE signature. The PE signature
 * word is followed by a {@link CoffFile COFF header}. Note that absolute offsets in the embedded
 * COFF file are relative to the whole file, not to the COFF header!
 * 
 * @see <a href=
 *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?redirectedfrom=MSDN#overview">PE
 *      Format</a>
 */
public final class PEFile {
    private static final short IMAGE_DOS_SIGNATURE = (short) 0x5A4D; // MZ
    private static final int IMAGE_NT_SIGNATURE = 0x00004550;  // PE00
    private static final int OFFSET_TO_PE_SIGNATURE = 0x3c;

    private final CoffFile coffFile;

    private PEFile(CoffFile coffFile) {
        this.coffFile = coffFile;
    }

    public CoffFile getCoffFile() {
        return coffFile;
    }

    public static PEFile create(ByteSequence bytes) {
        ObjectFileReader reader = new ObjectFileReader(bytes, true);
        short machine = reader.getShort();
        if (machine != IMAGE_DOS_SIGNATURE) {
            throw new LLVMParserException("Invalid MS DOS file!");
        }
        reader.setPosition(OFFSET_TO_PE_SIGNATURE);
        int peOffset = reader.getInt();
        reader.setPosition(peOffset);
        int reSignature = reader.getInt();
        if (reSignature != IMAGE_NT_SIGNATURE) {
            throw new LLVMParserException("No PE Signature found in MS DOS Executable!");
        }
        return new PEFile(CoffFile.create(bytes, reader));
    }
}
