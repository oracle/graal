/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tools;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import com.oracle.truffle.llvm.parser.binary.BinaryParser;
import com.oracle.truffle.llvm.parser.binary.BinaryParserResult;
import java.util.Arrays;
import org.graalvm.polyglot.io.ByteSequence;

public final class ExtractBitcode {

    private static class ArrayByteSequence implements ByteSequence {

        private final byte[] bytes;
        private final int length;

        ArrayByteSequence(byte[] bytes, int length) {
            this.bytes = bytes;
            this.length = length;
            assert bytes.length >= length;
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public byte byteAt(int index) {
            return bytes[index];
        }
    }

    private static ByteSequence readFully(InputStream in) throws IOException {
        byte[] buffer = new byte[1024];
        int length = 0;

        for (;;) {
            if (length == buffer.length) {
                buffer = Arrays.copyOf(buffer, length * 2);
            }
            int read = in.read(buffer, length, buffer.length - length);
            if (read < 0) {
                break;
            }

            length += read;
        }

        return new ArrayByteSequence(buffer, length);
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            usage(System.err);
            System.exit(1);
        }
        try {
            String inName = args[0];
            String outName = args[1];
            InputStream in = inName.equals("-") ? System.in : new FileInputStream(inName);
            OutputStream out = outName.equals("-") ? System.out : new FileOutputStream(outName);
            ByteSequence bytes = readFully(in);
            BinaryParserResult result = BinaryParser.parse(bytes, null, null);
            if (result == null) {
                throw new IOException("No bitcode found in file '" + inName + "'");
            }
            out.write(result.getBitcode().toByteArray());
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    private static void usage(PrintStream s) {
        s.println();
        s.println("Usage: extract-bitcode inputFile outputFile");
        s.println();
        s.println("Arguments:");
        s.println("  inputFile       An input file supported by the GraalVM");
        s.println("                  LLVM runtime (ELF, Mach-O, wrapped bitcode).");
        s.println("                  Use '-' for stdin.");
        s.println("  outputFile      The bitcode file to be written.");
        s.println("                  Use '-' for stdout.");
    }
}
