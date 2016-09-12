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
package uk.ac.man.cs.llvm.ir.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.man.cs.llvm.ir.model.constants.AggregateConstant;
import uk.ac.man.cs.llvm.ir.model.constants.ArrayConstant;
import uk.ac.man.cs.llvm.ir.model.constants.Constant;
import uk.ac.man.cs.llvm.ir.model.constants.StructureConstant;
import uk.ac.man.cs.llvm.ir.model.constants.VectorConstant;
import uk.ac.man.cs.llvm.ir.types.MetaType;
import uk.ac.man.cs.llvm.ir.types.ArrayType;
import uk.ac.man.cs.llvm.ir.types.StructureType;
import uk.ac.man.cs.llvm.ir.types.Type;
import uk.ac.man.cs.llvm.ir.types.VectorType;

public final class Symbols {

    private static final int INITIAL_CAPACITY = 16;

    private Symbol[] symbols;

    private int size;

    private final Map<ForwardReference, String> forwardReferenceNames = new HashMap<>();

    public Symbols() {
        symbols = new Symbol[INITIAL_CAPACITY];
    }

    public void addSymbol(Symbol symbol) {
        ensureCapacity(size + 1);

        if (symbols[size] != null) {
            final ForwardReference ref = (ForwardReference) symbols[size];
            ref.replace(symbol);
            if (forwardReferenceNames.containsKey(ref)) {
                if (symbol instanceof ValueSymbol) {
                    ((ValueSymbol) symbol).setName(forwardReferenceNames.get(ref));
                }
                forwardReferenceNames.remove(ref);
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

    public Constant getConstant(int index) {
        return (Constant) getSymbol(index);
    }

    public Constant[] getConstants(int[] indices) {
        Constant[] consts = new Constant[indices.length];

        for (int i = 0; i < indices.length; i++) {
            consts[i] = getConstant(indices[i]);
        }

        return consts;
    }

    public AggregateConstant createAggregate(Type type, int[] valueIndices) {
        final AggregateConstant aggregateConstant;
        if (type instanceof ArrayType) {
            aggregateConstant = new ArrayConstant((ArrayType) type, valueIndices.length);
        } else if (type instanceof StructureType) {
            aggregateConstant = new StructureConstant((StructureType) type, valueIndices.length);
        } else if (type instanceof VectorType) {
            aggregateConstant = new VectorConstant((VectorType) type, valueIndices.length);
        } else {
            throw new RuntimeException("No value constant implementation for " + type);
        }

        for (int i = 0; i < valueIndices.length; i++) {
            int index = valueIndices[i];
            final Constant value;
            if (index < size) {
                value = (Constant) symbols[index];
            } else {
                ensureCapacity(index + 1);
                if (symbols[index] == null) {
                    symbols[index] = new ForwardReference();
                }

                final ForwardReference ref = (ForwardReference) symbols[index];
                ref.addDependent(aggregateConstant, i);
                value = ref;
            }

            aggregateConstant.replaceElement(i, value);
        }

        return aggregateConstant;
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

    public Symbol[] getSymbols(int[] indices) {
        Symbol[] syms = new Symbol[indices.length];

        for (int i = 0; i < indices.length; i++) {
            syms[i] = getSymbol(indices[i]);
        }

        return syms;
    }

    public void setSymbolName(int index, String name) {
        Symbol symbol = getSymbol(index);
        if (symbol instanceof ForwardReference) {
            forwardReferenceNames.put((ForwardReference) symbol, name);
        } else if (symbol instanceof ValueSymbol) {
            ((ValueSymbol) symbol).setName(name);
        }
    }

    private void ensureCapacity(int capacity) {
        while (symbols.length < capacity) {
            symbols = Arrays.copyOf(symbols, symbols.length << 1);
        }
    }

    @Override
    public String toString() {
        return "Symbols [symbols=" + Arrays.toString(Arrays.copyOfRange(symbols, 0, size)) + ", size=" + size + ", forwardReferenceNames=" + forwardReferenceNames + "]";
    }

    private static class ForwardReference implements Constant {

        private final List<Symbol> dependents = new ArrayList<>();
        private final Map<AggregateConstant, Integer> aggregateDependents = new HashMap<>();

        ForwardReference() {
        }

        void addDependent(Symbol dependent) {
            dependents.add(dependent);
        }

        void addDependent(AggregateConstant dependent, int index) {
            aggregateDependents.put(dependent, index);
        }

        public void replace(Symbol replacement) {
            aggregateDependents.forEach((key, val) -> key.replaceElement(val, replacement));
            dependents.forEach(dependent -> dependent.replace(this, replacement));
        }

        @Override
        public Type getType() {
            return MetaType.UNKNOWN;
        }
    }
}
