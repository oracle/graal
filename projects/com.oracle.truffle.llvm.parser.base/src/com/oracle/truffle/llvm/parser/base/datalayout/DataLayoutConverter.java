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
package com.oracle.truffle.llvm.parser.base.datalayout;

import com.oracle.truffle.llvm.parser.LLVMBaseType;

import java.util.Arrays;
import java.util.List;

public class DataLayoutConverter {

    public static class DataSpecConverter {

        private final List<DataLayoutParser.DataTypeSpecification> dataLayout;

        DataSpecConverter(List<DataLayoutParser.DataTypeSpecification> dataLayout) {
            this.dataLayout = dataLayout;
        }

        public int getBitAlignment(LLVMBaseType baseType) {
            if (baseType == LLVMBaseType.I_VAR_BITWIDTH) {
                return 0;
            } else {
                return getDataTypeSpecification(baseType).getValues()[1];
            }
        }

        DataLayoutParser.DataTypeSpecification getDataTypeSpecification(LLVMBaseType baseType) {
            // Checkstyle: stop magic number name check
            switch (baseType) {
                case I1:
                    return locateDataTypeSpecification(DataLayoutType.INTEGER, 1);
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
                case ADDRESS:
                    return locateDataTypeSpecification(DataLayoutType.POINTER);
                case FUNCTION_ADDRESS:
                    return locateDataTypeSpecification(DataLayoutType.POINTER);
                default:
                    throw new AssertionError(baseType);
            }
            // Checkstyle: resume magic number name check
        }

        private DataLayoutParser.DataTypeSpecification locateDataTypeSpecification(DataLayoutType dataLayoutType, int... values) {
            for (DataLayoutParser.DataTypeSpecification spec : dataLayout) {
                CONT: if (spec.getType().equals(dataLayoutType)) {
                    for (int value : values) {
                        if (value != spec.getValues()[0]) {
                            break CONT;
                        }
                    }
                    return spec;
                }
            }
            throw new AssertionError(dataLayoutType + " " + Arrays.toString(values));
        }

    }

    public static DataSpecConverter getConverter(String layout) {
        final List<DataLayoutParser.DataTypeSpecification> dataLayout = DataLayoutParser.parseDataLayout(layout);
        return new DataSpecConverter(dataLayout);
    }

}
