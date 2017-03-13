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
package com.oracle.truffle.llvm.parser.datalayout;

import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.llvm.parser.datalayout.DataLayoutParser.DataTypeSpecification;
import com.oracle.truffle.llvm.runtime.types.DataSpecConverter;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;

public final class DataLayoutConverter {

    public static final class DataSpecConverterImpl implements DataSpecConverter {

        private final List<DataTypeSpecification> dataLayout;

        private DataSpecConverterImpl(List<DataTypeSpecification> dataLayout) {
            this.dataLayout = dataLayout;
        }

        @Override
        public int getSize(Type type) {
            return Math.max(1, getBitAlignment(type) / Byte.SIZE);
        }

        @Override
        public int getBitAlignment(Type baseType) {
            if (baseType instanceof VariableBitWidthType) {
                /*
                 * Handling of integer datatypes when the exact match not found
                 * http://releases.llvm.org/3.9.0/docs/LangRef.html#data-layout
                 */
                DataTypeSpecification integerLayout = dataLayout.stream().filter(d -> d.getType() == DataLayoutType.INTEGER_WIDTHS).findFirst().orElseThrow(IllegalStateException::new);
                int minPossibleSize = Arrays.stream(integerLayout.getValues()).max().orElseThrow(IllegalStateException::new);
                int size = ((VariableBitWidthType) baseType).getBitSize();
                for (int value : integerLayout.getValues()) {
                    if (size < value && minPossibleSize > value) {
                        minPossibleSize = value;
                    }
                }
                return minPossibleSize;
            } else {
                return getDataTypeSpecification(baseType).getValues()[1];
            }
        }

        private DataTypeSpecification getDataTypeSpecification(Type baseType) {
            if (baseType instanceof PointerType) {
                return locateDataTypeSpecification(DataLayoutType.POINTER);
            } else if (baseType instanceof FunctionType) {
                return locateDataTypeSpecification(DataLayoutType.POINTER);
            } else if (baseType instanceof PrimitiveType) {
                PrimitiveType primitiveType = (PrimitiveType) baseType;
                switch (primitiveType.getKind()) {
                    case I1:
                        return locateDataTypeSpecification(DataLayoutType.INTEGER, 8); // 1 is
                                                                                       // rounded
                                                                                       // up to
                                                                                       // 8
                    case I8:
                        return locateDataTypeSpecification(DataLayoutType.INTEGER, 8);
                    case I16:
                        return locateDataTypeSpecification(DataLayoutType.INTEGER, 16);
                    case I32:
                        return locateDataTypeSpecification(DataLayoutType.INTEGER, 32);
                    case I64:
                        return locateDataTypeSpecification(DataLayoutType.INTEGER, 64);
                    case HALF:
                        return locateDataTypeSpecification(DataLayoutType.FLOAT, 16);
                    case FLOAT:
                        return locateDataTypeSpecification(DataLayoutType.FLOAT, 32);
                    case DOUBLE:
                        return locateDataTypeSpecification(DataLayoutType.FLOAT, 64);
                    case X86_FP80:
                        return locateDataTypeSpecification(DataLayoutType.FLOAT, 80);
                }
            }
            throw new AssertionError(baseType);
        }

        private DataTypeSpecification locateDataTypeSpecification(DataLayoutType dataLayoutType) {
            for (DataTypeSpecification spec : dataLayout) {
                if (spec.getType().equals(dataLayoutType)) {
                    return spec;
                }
            }
            throw new AssertionError(dataLayoutType);
        }

        private DataTypeSpecification locateDataTypeSpecification(DataLayoutType dataLayoutType, int size) {
            for (DataTypeSpecification spec : dataLayout) {
                if (spec.getType().equals(dataLayoutType) && size == spec.getValues()[0]) {
                    return spec;
                }
            }
            throw new AssertionError(dataLayoutType + " size: " + size);
        }

    }

    public static DataSpecConverterImpl getConverter(String layout) {
        final List<DataTypeSpecification> dataLayout = DataLayoutParser.parseDataLayout(layout);
        return new DataSpecConverterImpl(dataLayout);
    }

}
