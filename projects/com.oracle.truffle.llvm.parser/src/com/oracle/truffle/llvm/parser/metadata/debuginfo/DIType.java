/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import java.util.function.Supplier;

public class DIType {
    static final String COUNT_NAME = "<count>";
    static final String ANON_NAME = MDNameExtractor.DEFAULT_STRING;

    static final DIType UNKNOWN_TYPE = new DIType(() -> "<unknown>", 0, 0, 0);

    private Supplier<String> nameSupplier;

    private long size;

    private long align;

    private long offset;

    DIType(Supplier<String> nameSupplier, long size, long align, long offset) {
        this.nameSupplier = nameSupplier;
        this.size = size;
        this.align = align;
        this.offset = offset;
    }

    DIType(long size, long align, long offset) {
        this(null, size, align, offset);
    }

    public String getName() {
        return nameSupplier.get();
    }

    public long getSize() {
        return size;
    }

    public long getAlign() {
        return align;
    }

    public long getOffset() {
        return offset;
    }

    public void setName(Supplier<String> nameSupplier) {
        this.nameSupplier = nameSupplier;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setAlign(long align) {
        this.align = align;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    DIType getOffset(long newOffset) {
        return new DIType(nameSupplier, size, align, newOffset);
    }

    @Override
    public String toString() {
        return getName();
    }
}
