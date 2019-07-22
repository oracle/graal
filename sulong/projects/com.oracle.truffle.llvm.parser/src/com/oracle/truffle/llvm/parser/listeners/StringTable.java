/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.listeners;

import com.oracle.truffle.llvm.parser.model.ValueSymbol;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;

import java.util.ArrayList;
import java.util.List;

final class StringTable implements ParserListener {

    private static final long BYTE_MASK = 0xffL;

    private final List<NameRequest> requests = new ArrayList<>();

    private String table = null;

    StringTable() {
    }

    @Override
    public void record(RecordBuffer buffer) {
        byte[] bytes = new byte[buffer.size() * Long.BYTES];
        int curByte = 0;
        while (buffer.remaining() > 0) {
            long l = buffer.read();
            for (int j = 0; j < Long.BYTES; j++) {
                bytes[curByte++] = (byte) (l & BYTE_MASK);
                l >>>= Byte.SIZE;
            }
        }
        table = new String(bytes);
    }

    @Override
    public void exit() {
        for (NameRequest request : requests) {
            request.resolve();
        }
        requests.clear();
    }

    void requestName(int offset, int length, ValueSymbol target) {
        if (length <= 0 || offset < 0) {
            return;
        }
        // the STRTAB block's content may be forward referenced
        if (table != null) {
            target.setName(get(offset, length));
        } else {
            requests.add(new NameRequest(offset, length, target));
        }
    }

    private String get(int offset, int size) {
        if (offset + size < table.length()) {
            return table.substring(offset, offset + size);
        } else {
            return "";
        }
    }

    private final class NameRequest {

        private final int offset;
        private final int length;
        private final ValueSymbol target;

        private NameRequest(int offset, int length, ValueSymbol target) {
            this.offset = offset;
            this.length = length;
            this.target = target;
        }

        void resolve() {
            target.setName(get(offset, length));
        }
    }
}
