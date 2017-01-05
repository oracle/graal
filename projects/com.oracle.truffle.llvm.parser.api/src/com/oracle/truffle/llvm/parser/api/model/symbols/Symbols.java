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
package com.oracle.truffle.llvm.parser.api.model.symbols;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.llvm.parser.api.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.api.model.symbols.constants.aggregate.AggregateConstant;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

public final class Symbols {

    private static final int INITIAL_CAPACITY = 16;

    private Symbol[] symbols;

    private int size;

    public Symbols() {
        symbols = new Symbol[INITIAL_CAPACITY];
    }

    public void addSymbol(Symbol symbol) {
        ensureCapacity(size + 1);

        if (symbols[size] != null) {
            final ForwardReference ref = (ForwardReference) symbols[size];
            ref.replace(symbol);
            if (ref.getName() != null && symbol instanceof ValueSymbol) {
                ((ValueSymbol) symbol).setName(ref.getName());
            }
            ((ForwardReference) symbols[size]).replace(symbol);
        }
        symbols[size++] = symbol;
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
            ensureCapacity(index + 1);

            ForwardReference ref = (ForwardReference) symbols[index];
            if (ref == null) {
                symbols[index] = ref = new ForwardReference();
            }
            ref.addDependent(dependent);

            return ref;
        }
    }

    public Symbol getSymbol(int index, AggregateConstant dependent, int elementIndex) {
        if (index < size) {
            return symbols[index];
        } else {
            ensureCapacity(index + 1);

            ForwardReference ref = (ForwardReference) symbols[index];
            if (ref == null) {
                symbols[index] = ref = new ForwardReference();
            }
            ref.addDependent(dependent, elementIndex);

            return ref;
        }
    }

    public void setSymbolName(int index, String name) {
        Symbol symbol = getSymbol(index);
        if (symbol instanceof ValueSymbol) {
            ((ValueSymbol) symbol).setName(name);
        }

        if (index < size) {
            if (symbols[index] instanceof ValueSymbol) {
                ((ValueSymbol) symbols[index]).setName(name);
            }
        } else {
            ensureCapacity(index + 1);

            ForwardReference ref = (ForwardReference) symbols[index];
            if (ref == null) {
                symbols[index] = ref = new ForwardReference();
            }
            ref.setName(name);
        }
    }

    private void ensureCapacity(int capacity) {
        while (symbols.length < capacity) {
            symbols = Arrays.copyOf(symbols, symbols.length << 1);
        }
    }

    @Override
    public String toString() {
        return "Symbols [symbols=" + Arrays.toString(Arrays.copyOfRange(symbols, 0, size)) + ", size=" + size + "]";
    }

    private static final class ForwardReference implements Constant, ValueSymbol {

        private final List<Symbol> dependents = new ArrayList<>();
        private final Map<AggregateConstant, List<Integer>> aggregateDependents = new HashMap<>();

        private String name;

        ForwardReference() {
            this.name = null;
        }

        void addDependent(Symbol dependent) {
            dependents.add(dependent);
        }

        void addDependent(AggregateConstant dependent, int index) {
            final List<Integer> indices = aggregateDependents.getOrDefault(dependent, new ArrayList<>());
            indices.add(index);
            aggregateDependents.put(dependent, indices);
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
            return String.format("ForwardReference[name=%s]", name == null ? UNKNOWN : name);
        }
    }
}
