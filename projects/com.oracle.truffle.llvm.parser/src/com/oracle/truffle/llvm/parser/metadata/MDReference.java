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
package com.oracle.truffle.llvm.parser.metadata;

import com.oracle.truffle.llvm.runtime.types.MetaType;

public abstract class MDReference extends MDTypedValue implements MDBaseNode {

    private static final class MDRef extends MDReference {

        private final int index;

        private final MetadataList md;

        private MDRef(int index, MetadataList md) {
            this.index = index;
            this.md = md;
        }

        @Override
        public MDBaseNode get() {
            return md.getFromRef(index);
        }

        @Override
        public String toString() {
            return String.format("!%d", index);
        }
    }

    private static final class SymRef extends MDReference {

        private final MDBaseNode base;

        private SymRef(MDBaseNode base) {
            this.base = base;
        }

        @Override
        public MDBaseNode get() {
            return base;
        }

        @Override
        public String toString() {
            try {
                return String.format("%s %s", getType(), get());
            } catch (Exception e) {
                return String.format("%s forwardref", getType());
            }
        }
    }

    public static final MDReference VOID = new MDReference() {

        @Override
        public MDBaseNode get() {
            throw new IndexOutOfBoundsException("VOID cannot be dereferenced!");
        }

        @Override
        public String toString() {
            return "VOID";
        }
    };

    public static MDReference fromIndex(int index, MetadataList md) {
        return new MDRef(index, md);
    }

    public static MDReference fromSymbolRef(MDSymbolReference ref) {
        return new SymRef(MDValue.createFromSymbolReference(ref));
    }

    private MDReference() {
        super(MetaType.METADATA);
    }

    public abstract MDBaseNode get();

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }
}
