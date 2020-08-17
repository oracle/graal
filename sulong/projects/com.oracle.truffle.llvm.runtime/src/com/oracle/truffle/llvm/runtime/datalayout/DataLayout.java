/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.datalayout;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;

import com.oracle.truffle.llvm.runtime.datalayout.DataLayoutParser.DataTypeSpecification;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;

/**
 * Each LLVM bitcode file contains a data layout header, that determines which data types are
 * available and which alignment is used for each datatype. At the moment, this is mainly used to
 * determine the size of variable bit width integer values.
 *
 * Besides that, this class is hardly used and most other Sulong code parts contain hard-coded
 * assumptions regarding sizes/alignments...
 */
public final class DataLayout {

    private final ArrayList<DataTypeSpecification> dataLayout;
    private final ByteOrder byteOrder;

    private final IdentityHashMap<Type, Long> sizeCache = new IdentityHashMap<>();
    private final IdentityHashMap<Type, Integer> alignmentCache = new IdentityHashMap<>();

    public DataLayout(ByteOrder byteOrder) {
        this.dataLayout = new ArrayList<>();
        this.byteOrder = byteOrder;
    }

    public DataLayout(String layout) {
        this.dataLayout = new ArrayList<>();
        this.byteOrder = DataLayoutParser.parseDataLayout(layout, dataLayout);
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public long getSize(Type type) throws TypeOverflowException {
        Long cachedSize = sizeCache.get(type);
        if (cachedSize != null) {
            return cachedSize;
        }
        long size = type.getBitSize();
        int align = getBitAlignment(type);
        long rem = Long.remainderUnsigned(size, align);
        if (rem != 0) {
            size = Type.addUnsignedExact(size, Type.subUnsignedExact(align, rem));
        }
        size = Math.max(1, Long.divideUnsigned(size, Byte.SIZE));
        sizeCache.put(type, size);
        return size;
    }

    public int getBitAlignment(Type baseType) {
        Integer cachedAlignment = alignmentCache.get(baseType);
        if (cachedAlignment != null) {
            return cachedAlignment;
        }
        DataTypeSpecification spec = getDataTypeSpecification(baseType);
        if (spec == null) {
            throw new IllegalStateException("No data specification found for " + baseType + ". Data layout is " + dataLayout);
        }
        int alignment = spec.getAbiAlignment();
        alignmentCache.put(baseType, alignment);
        return alignment;
    }

    public DataLayout merge(DataLayout other) {
        if (other.byteOrder != byteOrder) {
            throw new IllegalStateException("Multiple bitcode files with incompatible byte order are used: " + this.toString() + " vs. " + other.toString());
        }
        DataLayout result = new DataLayout(byteOrder);
        for (DataTypeSpecification otherEntry : other.dataLayout) {
            DataTypeSpecification thisEntry;
            if (otherEntry.getType() == DataLayoutType.POINTER || otherEntry.getType() == DataLayoutType.INTEGER_WIDTHS) {
                thisEntry = getDataTypeSpecification(otherEntry.getType());
            } else if (otherEntry.getType() == DataLayoutType.INTEGER || otherEntry.getType() == DataLayoutType.FLOAT) {
                thisEntry = getDataTypeSpecification(otherEntry.getType(), otherEntry.getSize());
            } else {
                throw new IllegalStateException("Unknown data layout type: " + otherEntry.getType());
            }

            result.dataLayout.add(otherEntry);
            if (thisEntry != null && !thisEntry.equals(otherEntry)) {
                throw new IllegalStateException("Multiple bitcode files with incompatible layout strings are used: " + this.toString() + " vs. " + other.toString());
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return dataLayout.toString();
    }

    private DataTypeSpecification getDataTypeSpecification(Type baseType) {
        if (baseType instanceof PointerType || baseType instanceof FunctionType) {
            DataTypeSpecification ptrDTSpec = getDataTypeSpecification(DataLayoutType.POINTER, 64);
            // The preceding call does not work for ARM arch that uses 128 bit pointers. In that
            // case we take the first pointer spec available.
            return ptrDTSpec == null ? getDataTypeSpecification(DataLayoutType.POINTER) : ptrDTSpec;
        } else if (baseType instanceof PrimitiveType) {
            PrimitiveType primitiveType = (PrimitiveType) baseType;
            switch (primitiveType.getPrimitiveKind()) {
                case I1:
                case I8:
                    // 1 is rounded up to 8 as well
                    return getDataTypeSpecification(DataLayoutType.INTEGER, 8);
                case I16:
                    return getDataTypeSpecification(DataLayoutType.INTEGER, 16);
                case I32:
                    return getDataTypeSpecification(DataLayoutType.INTEGER, 32);
                case I64:
                    return getDataTypeSpecification(DataLayoutType.INTEGER, 64);
                case HALF:
                    return getDataTypeSpecification(DataLayoutType.FLOAT, 16);
                case FLOAT:
                    return getDataTypeSpecification(DataLayoutType.FLOAT, 32);
                case DOUBLE:
                    return getDataTypeSpecification(DataLayoutType.FLOAT, 64);
                case X86_FP80:
                    return getDataTypeSpecification(DataLayoutType.FLOAT, 80);
            }
        } else if (baseType instanceof VariableBitWidthType) {
            int bits = ((VariableBitWidthType) baseType).getBitSizeInt();

            DataTypeSpecification largest = null;
            DataTypeSpecification smallestLarger = null;
            for (DataTypeSpecification spec : dataLayout) {
                if (spec.getType() == DataLayoutType.INTEGER) {
                    if (largest == null || largest.getSize() < spec.getSize()) {
                        largest = spec;
                    }
                    if (spec.getSize() >= bits) {
                        if (smallestLarger == null || smallestLarger.getSize() > spec.getSize()) {
                            smallestLarger = spec;
                        }
                    }
                }
            }

            // http://releases.llvm.org/3.9.0/docs/LangRef.html#data-layout
            if (smallestLarger != null) {
                /*
                 * If no match is found, and the type sought is an integer type, then the smallest
                 * integer type that is larger than the bitwidth of the sought type is used.
                 */
                return smallestLarger;
            } else {
                /*
                 * If none of the specifications are larger than the bitwidth then the largest
                 * integer type is used.
                 */
                return largest;
            }
        }
        return null;
    }

    private DataTypeSpecification getDataTypeSpecification(DataLayoutType dataLayoutType) {
        for (DataTypeSpecification spec : dataLayout) {
            if (spec.getType().equals(dataLayoutType)) {
                return spec;
            }
        }
        return null;
    }

    private DataTypeSpecification getDataTypeSpecification(DataLayoutType dataLayoutType, int size) {
        for (DataTypeSpecification spec : dataLayout) {
            if (spec.getType().equals(dataLayoutType) && size == spec.getSize()) {
                return spec;
            }
        }
        return null;
    }
}
