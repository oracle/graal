/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata;

import com.oracle.truffle.llvm.parser.scanner.BitStream;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;

public final class MDString implements MDBaseNode {

    private final String s;

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private MDString(String s) {
        this.s = s;
    }

    public String getString() {
        return s;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
    }

    @Override
    public String toString() {
        return String.format("!\"%s\"", s);
    }

    public static MDString create(RecordBuffer buffer) {
        return new MDString(buffer.readUnicodeString());
    }

    private static final int STRINGS_ARGINDEX_COUNT = 0;
    private static final int STRINGS_ARGINDEX_STROFFSET = 1;
    private static final int STRINGS_ARGOFFSET_BLOB = 2;
    private static final int STRINGS_SIZE_WIDTH = 6;

    public static MDString[] createFromBlob(long[] args) {
        // the number of strings in the attached blob
        final long count = args[STRINGS_ARGINDEX_COUNT];

        final MDString[] strings = new MDString[Math.toIntExact(count)];

        final BitStream blob = BitStream.createFromBlob(args, STRINGS_ARGOFFSET_BLOB);
        long strOffset = args[STRINGS_ARGINDEX_STROFFSET] * Byte.SIZE;
        long lenOffset = 0;
        for (int i = 0; i < count; i++) {
            long size = blob.readVBR(lenOffset, STRINGS_SIZE_WIDTH);
            lenOffset += BitStream.widthVBR(size, STRINGS_SIZE_WIDTH);

            final byte[] bytes = new byte[(int) (size)];
            int j = 0;
            while (size > 0) {
                bytes[j++] = (byte) blob.read(strOffset, Byte.SIZE);
                size--;
                strOffset += Byte.SIZE;
            }

            strings[i] = new MDString(new String(bytes));
        }
        return strings;
    }

    public static String getIfInstance(MDBaseNode strNode) {
        if (strNode instanceof MDString) {
            return ((MDString) strNode).getString();
        } else {
            return null;
        }
    }
}
