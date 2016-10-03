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
package com.oracle.truffle.llvm.parser.base.model.symbols.constants;

import com.oracle.truffle.llvm.parser.base.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.base.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.types.VectorType;

import java.util.Arrays;

public abstract class AggregateConstant extends AbstractConstant {

    private final Symbol[] elements;

    AggregateConstant(Type type, Symbol[] elements) {
        super(type);
        this.elements = elements;
    }

    AggregateConstant(Type type, int size) {
        super(type);
        this.elements = new Symbol[size];
    }

    public Symbol getElement(int idx) {
        return elements[idx];
    }

    public int getElementCount() {
        return elements.length;
    }

    public void fill(Constant element) {
        Arrays.fill(elements, element);
    }

    public Symbol[] getElements() {
        return Arrays.copyOf(elements, elements.length);
    }

    public void replaceElement(int index, Symbol replacement) {
        if (!(replacement instanceof Constant || replacement instanceof GlobalValueSymbol)) {
            throw new IllegalStateException("Values can only be replaced by Constants or Globals!");
        }
        elements[index] = replacement;
    }

    protected String getContent() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < getElementCount(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Symbol value = getElement(i);
            sb.append(value.getType()).append(" ").append(value);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < getElementCount(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Symbol value = getElement(i);
            sb.append(value.getType()).append(" ").append(value);
        }
        return sb.toString();
    }

    static AggregateConstant fromData(Type type, long[] data) {
        final AggregateConstant aggregateConstant;
        final Type elementType;
        if (type instanceof ArrayType) {
            final ArrayType arrayType = (ArrayType) type;
            elementType = arrayType.getElementType();
            aggregateConstant = new ArrayConstant(arrayType, data.length);
        } else if (type instanceof VectorType) {
            final VectorType vectorType = (VectorType) type;
            elementType = vectorType.getElementType();
            aggregateConstant = new VectorConstant((VectorType) type, data.length);
        } else {
            throw new RuntimeException("Cannot create constant from data: " + type);
        }

        for (int i = 0; i < data.length; i++) {
            aggregateConstant.replaceElement(i, Constant.createFromData(elementType, data[i]));
        }

        return aggregateConstant;
    }

    static AggregateConstant fromSymbols(Symbols symbols, Type type, int[] valueIndices) {
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

        for (int elementIndex = 0; elementIndex < valueIndices.length; elementIndex++) {
            aggregateConstant.replaceElement(elementIndex, symbols.getSymbol(valueIndices[elementIndex], aggregateConstant, elementIndex));
        }

        return aggregateConstant;
    }
}
