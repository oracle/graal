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
package com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate;

import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;
import com.oracle.truffle.llvm.parser.model.symbols.constants.AbstractConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatingPointConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;

public abstract class AggregateConstant extends AbstractConstant {

    private final SymbolImpl[] elements;

    AggregateConstant(Type type, int size) {
        super(type);
        this.elements = new SymbolImpl[size];
    }

    public SymbolImpl getElement(int idx) {
        return elements[idx];
    }

    public int getElementCount() {
        return elements.length;
    }

    @Override
    public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
        if (!(newValue instanceof Constant || newValue instanceof GlobalValueSymbol)) {
            throw new LLVMParserException("Values can only be replaced by Constants or Globals!");
        }
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] == oldValue) {
                elements[i] = newValue;
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < getElementCount(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            SymbolImpl value = getElement(i);
            sb.append(value.getType()).append(" ").append(value);
        }
        return sb.toString();
    }

    public static AggregateConstant createFromData(Type type, RecordBuffer buffer) {
        final AggregateConstant aggregateConstant;
        final Type elementType;
        if (type instanceof ArrayType) {
            final ArrayType arrayType = (ArrayType) type;
            elementType = arrayType.getElementType();
            aggregateConstant = new ArrayConstant(arrayType, buffer.size());
        } else if (type instanceof VectorType) {
            final VectorType vectorType = (VectorType) type;
            elementType = vectorType.getElementType();
            aggregateConstant = new VectorConstant((VectorType) type, buffer.size());
        } else {
            throw new LLVMParserException("Cannot create constant from data: " + type);
        }

        if (Type.isIntegerType(elementType)) {
            for (int i = 0; i < aggregateConstant.elements.length; i++) {
                aggregateConstant.elements[i] = IntegerConstant.createFromData(elementType, buffer);
            }
        } else if (Type.isFloatingpointType(elementType)) {
            for (int i = 0; i < aggregateConstant.elements.length; i++) {
                aggregateConstant.elements[i] = FloatingPointConstant.create(elementType, buffer);
            }
        } else {
            throw new LLVMParserException("No datum constant implementation for " + type);
        }

        return aggregateConstant;
    }

    public static AggregateConstant createFromValues(SymbolTable symbols, Type type, RecordBuffer buffer) {
        final AggregateConstant aggregateConstant;
        int length = buffer.remaining();
        if (type instanceof ArrayType) {
            aggregateConstant = new ArrayConstant((ArrayType) type, length);
        } else if (type instanceof StructureType) {
            aggregateConstant = new StructureConstant((StructureType) type, length);
        } else if (type instanceof VectorType) {
            aggregateConstant = new VectorConstant((VectorType) type, length);
        } else {
            throw new LLVMParserException("Cannot create constant for type: " + type);
        }

        for (int elementIndex = 0; elementIndex < length; elementIndex++) {
            aggregateConstant.elements[elementIndex] = symbols.getForwardReferenced(buffer.readInt(), aggregateConstant);
        }

        return aggregateConstant;
    }
}
