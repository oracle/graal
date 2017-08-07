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
package com.oracle.truffle.llvm.parser.model.symbols;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.AggregateConstant;
import com.oracle.truffle.llvm.parser.model.visitors.ConstantVisitor;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

public final class Symbols {

    private static final int INITIAL_CAPACITY = 16;

    private Symbol[] symbols;

    private int size;

    public Symbols() {
        symbols = new Symbol[INITIAL_CAPACITY];
    }

    public Symbol getOrNull(int index) {
        return index < size && index >= 0 ? getSymbol(index) : null;
    }

    public void addSymbol(Symbol symbol) {
        ensureIndexExists(size);

        if (symbols[size] != null) {
            final ForwardReference ref = (ForwardReference) symbols[size];
            ref.replace(symbol);

            if (ref.getName() != null && symbol instanceof ValueSymbol) {
                ((ValueSymbol) symbol).setName(ref.getName());
            }

            if (ref.hasAttachedMetadata() && symbol instanceof MetadataAttachmentHolder) {
                for (MDAttachment attachment : ref.getAttachedMetadata()) {
                    ((MetadataAttachmentHolder) symbol).attachMetadata(attachment);
                }
            }
        }

        symbols[size++] = symbol;
    }

    private ForwardReference getForwardReference(int index) {
        ensureIndexExists(index);
        if (symbols[index] == null) {
            final ForwardReference ref = new ForwardReference();
            symbols[index] = ref;
            return ref;
        } else {
            return (ForwardReference) symbols[index];
        }
    }

    public void attachMetadata(int index, MDAttachment attachment) {
        if (index < size) {
            if (symbols[index] instanceof MetadataAttachmentHolder) {
                ((MetadataAttachmentHolder) symbols[index]).attachMetadata(attachment);
            }

        } else {
            getForwardReference(index).attachMetadata(attachment);
        }
    }

    public void addSymbols(Symbols argSymbols) {
        for (int i = 0; i < argSymbols.size; i++) {
            addSymbol(argSymbols.symbols[i]);
        }
    }

    public int getSize() {
        return size;
    }

    public Symbol getSymbol(int index) {
        if (index < size) {
            return symbols[index];
        }

        throw new IllegalStateException("Dependent required for forward references");
    }

    public Symbol getSymbol(int index, Symbol dependent) {
        if (index < size) {
            return symbols[index];
        } else {
            final ForwardReference ref = getForwardReference(index);
            ref.addDependent(dependent);
            return ref;
        }
    }

    public Symbol getSymbol(int index, AggregateConstant dependent, int elementIndex) {
        if (index < size) {
            return symbols[index];
        } else {
            final ForwardReference ref = getForwardReference(index);
            ref.addDependent(dependent, elementIndex);
            return ref;
        }
    }

    public void setSymbolName(int index, String name) {
        if (index < size) {
            if (symbols[index] instanceof ValueSymbol) {
                ((ValueSymbol) symbols[index]).setName(name);
            }
        } else {
            getForwardReference(index).setName(name);
        }
    }

    private void ensureIndexExists(int index) {
        if (index < symbols.length) {
            return;
        }

        int newLength = symbols.length;
        while (index >= newLength) {
            newLength <<= 1;
        }
        symbols = Arrays.copyOf(symbols, newLength);
    }

    @Override
    public String toString() {
        return String.format("Symbols [size = %d]", size);
    }

    private static final class ForwardReference implements Constant, ValueSymbol, MetadataAttachmentHolder {

        private final List<Symbol> dependents = new ArrayList<>();
        private final Map<AggregateConstant, List<Integer>> aggregateDependents = new HashMap<>();
        private List<MDAttachment> attachedMetadata = null;

        private String name;

        ForwardReference() {
            this.name = null;
        }

        @Override
        public void accept(ConstantVisitor visitor) {
        }

        void addDependent(Symbol dependent) {
            dependents.add(dependent);
        }

        void addDependent(AggregateConstant dependent, int index) {
            final List<Integer> indices = aggregateDependents.getOrDefault(dependent, new ArrayList<>());
            indices.add(index);
            aggregateDependents.put(dependent, indices);
        }

        @Override
        public boolean hasAttachedMetadata() {
            return attachedMetadata != null;
        }

        @Override
        public List<MDAttachment> getAttachedMetadata() {
            if (attachedMetadata == null) {
                attachedMetadata = new ArrayList<>();
            }
            return attachedMetadata;
        }

        public void replace(Symbol replacement) {
            aggregateDependents.forEach((key, val) -> val.forEach(i -> key.replaceElement(i, replacement)));
            dependents.forEach(dependent -> dependent.replace(this, replacement));
        }

        @Override
        public Type getType() {
            return MetaType.UNKNOWN;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format("ForwardReference[name=%s]", name == null ? LLVMIdentifier.UNKNOWN : name);
        }
    }
}
