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
package uk.ac.man.cs.llvm.ir.model.constants;

import uk.ac.man.cs.llvm.bc.records.Records;
import uk.ac.man.cs.llvm.ir.model.Symbol;
import uk.ac.man.cs.llvm.ir.types.ArrayType;
import uk.ac.man.cs.llvm.ir.types.FloatingPointType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.StructureType;
import uk.ac.man.cs.llvm.ir.types.Type;
import uk.ac.man.cs.llvm.ir.types.VectorType;

public interface Constant extends Symbol {

    static Constant createFromData(Type type, long datum) {
        if (type instanceof IntegerType) {
            IntegerType t = (IntegerType) type;

            // Sign extend for everything except i1 (boolean)
            int bits = t.getBitCount();
            long d = datum;
            if (bits > 1 && bits < Long.SIZE) {
                d = Records.extendSign(bits, d);
            }

            return new IntegerConstant(t, d);
        }

        if (type instanceof FloatingPointType) {
            return new FloatingPointConstant((FloatingPointType) type, datum);
        }

        throw new RuntimeException("No datum constant implementation for " + type);
    }

    static Constant createFromData(Type type, long[] data) {
        if (type instanceof ArrayType) {
            ArrayType array = (ArrayType) type;
            Type subtype = array.getElementType();
            Constant[] elements = new Constant[data.length];
            for (int i = 0; i < data.length; i++) {
                elements[i] = createFromData(subtype, data[i]);
            }
            return new ArrayConstant(array, elements);
        }

        if (type instanceof VectorType) {
            VectorType vector = (VectorType) type;
            Type subtype = vector.getElementType();
            Constant[] elements = new Constant[data.length];
            for (int i = 0; i < data.length; i++) {
                elements[i] = createFromData(subtype, data[i]);
            }
            return new VectorConstant(vector, elements);
        }

        throw new RuntimeException("No data constant implementation for " + type);
    }

    static Constant createFromValues(Type type, Constant[] values) {
        if (type instanceof ArrayType) {
            return new ArrayConstant((ArrayType) type, values);
        }

        if (type instanceof StructureType) {
            return new StructureConstant((StructureType) type, values);
        }

        if (type instanceof VectorType) {
            return new VectorConstant((VectorType) type, values);
        }

        throw new RuntimeException("No value constant implementation for " + type);
    }
}
