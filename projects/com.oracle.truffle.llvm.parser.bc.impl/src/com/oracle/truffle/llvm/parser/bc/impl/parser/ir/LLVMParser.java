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
package com.oracle.truffle.llvm.parser.bc.impl.parser.ir;

import com.oracle.truffle.llvm.parser.base.model.generators.ApplicationGenerator;
import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.Bitstream;
import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.Operation;
import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.Parser;
import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.ParserResult;
import com.oracle.truffle.llvm.parser.bc.impl.parser.listeners.module.Module;
import com.oracle.truffle.llvm.parser.bc.impl.parser.listeners.ModuleVersion;

public final class LLVMParser {

    private static final long MAGIC_WORD = 0xdec04342L; // 'BC' c0de
    private static final long WRAPPER_MAGIC_WORD = 0x0B17C0DEL;

    private final ApplicationGenerator generator;

    public LLVMParser(ApplicationGenerator generator) {
        this.generator = generator;
    }

    public void parse(ModuleVersion version, String bitcode) {
        Bitstream stream = Bitstream.create(bitcode);

        Module module = version.createModule(generator.createModule());

        Parser parser = new Parser(stream, module);

        BitcodeStreamInformation bcStreamInfo = getStreamInformation(stream, parser);
        parser = new Parser(stream, module, bcStreamInfo.offset);

        ParserResult result = parser.read(Integer.SIZE);
        if (result.getValue() != MAGIC_WORD) {
            generator.error("Illegal file (does not exist or contains no magic word)");
        }
        parser = result.getParser();

        while (parser.getOffset() < bcStreamInfo.totalStreamSize()) {
            result = parser.readId();
            Operation operation = parser.getOperation(result.getValue());
            parser = operation.apply(result.getParser());
        }
    }

    private static BitcodeStreamInformation getStreamInformation(Bitstream stream, Parser parser) {
        ParserResult first32bit = parser.read(Integer.SIZE);
        if (first32bit.getValue() == WRAPPER_MAGIC_WORD) {
            // offset and size of bitcode stream are specified in bitcode wrapper
            return parseWrapperFormatPrefix(first32bit.getParser());
        } else {
            return new BitcodeStreamInformation(0, stream.size());
        }
    }

    /*
     * Bitcode files can have a wrapper prefix: [Magic32, Version32, Offset32, Size32, CPUType32]
     * see: http://llvm.org/docs/BitCodeFormat.html#bitcode-wrapper-format
     */
    private static BitcodeStreamInformation parseWrapperFormatPrefix(Parser parser) {
        Parser p = parser;
        // Version32
        ParserResult value32Bit = p.read(Integer.SIZE);
        p = value32Bit.getParser();
        // Offset32
        value32Bit = p.read(Integer.SIZE);
        long offset = value32Bit.getValue() * Byte.SIZE;
        p = value32Bit.getParser();
        // Size32
        value32Bit = p.read(Integer.SIZE);
        long size = value32Bit.getValue() * Byte.SIZE;
        p = value32Bit.getParser();
        // CPUType32
        p.read(Integer.SIZE);
        // End of Wrapper Prefix

        return new BitcodeStreamInformation(offset, size);
    }

    private static class BitcodeStreamInformation {
        private final long offset;
        private final long size;

        BitcodeStreamInformation(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }

        private long totalStreamSize() {
            return offset + size;
        }
    }
}
