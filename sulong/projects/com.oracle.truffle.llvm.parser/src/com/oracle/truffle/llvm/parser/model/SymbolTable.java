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
package com.oracle.truffle.llvm.parser.model;

import com.oracle.truffle.llvm.parser.ValueList;
import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public final class SymbolTable extends ValueList<SymbolImpl, SymbolVisitor> {

    private static final PlaceholderFactory<SymbolImpl, SymbolVisitor> PLACEHOLDER_FACTORY = () -> new SymbolImpl() {

        @Override
        public Type getType() {
            return VoidType.INSTANCE;
        }

        @Override
        public void accept(SymbolVisitor visitor) {
            throw new LLVMParserException("Unresolved Forward Reference!");
        }

        @Override
        public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
            throw new LLVMParserException("Cannot replace Symbol in Forward Reference!");
        }

        @Override
        public String toString() {
            return "Forward Referenced Symbol";
        }
    };

    public SymbolTable() {
        super(PLACEHOLDER_FACTORY);
    }

    public void nameSymbol(int index, String name) {
        final SymbolImpl symbol = getOrNull(index);
        if (symbol instanceof ValueSymbol) {
            ((ValueSymbol) symbol).setName(name);

        } else if (symbol == null) {
            onParse(index, s -> {
                if (s instanceof ValueSymbol) {
                    ((ValueSymbol) s).setName(name);
                }
            });
        }
    }

    public void attachMetadata(int index, MDAttachment attachment) {
        final SymbolImpl symbol = getOrNull(index);
        if (symbol instanceof MetadataAttachmentHolder) {
            ((MetadataAttachmentHolder) symbol).attachMetadata(attachment);

        } else if (symbol == null) {
            onParse(index, s -> {
                if (s instanceof MetadataAttachmentHolder) {
                    ((MetadataAttachmentHolder) s).attachMetadata(attachment);
                }
            });
        }
    }
}
