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
package com.oracle.truffle.llvm.runtime.datalayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class DataLayoutParser {

    static final class DataTypeSpecification {

        private final DataLayoutType type;
        private final int[] values;

        private DataTypeSpecification(DataLayoutType type, int[] values) {
            this.type = type;
            this.values = values;
        }

        DataLayoutType getType() {
            return type;
        }

        int[] getValues() {
            return values;
        }

        @Override
        public String toString() {
            return getType() + " " + Arrays.toString(getValues());
        }

    }

    private static void addIfMissing(List<DataTypeSpecification> specs, DataTypeSpecification newSpec) {
        for (DataTypeSpecification spec : specs) {
            if (spec.type == newSpec.type && spec.values[0] == newSpec.values[0]) {
                return;
            }
        }
        specs.add(newSpec);
    }

    static List<DataTypeSpecification> parseDataLayout(String layout) {
        String[] layoutSpecs = layout.split("-");
        assertNoNullElement(layoutSpecs);
        List<DataTypeSpecification> specs = new ArrayList<>();
        for (String spec : layoutSpecs) {
            if (spec.equals("e") || spec.equals("E") || spec.equals("m:e") || spec.equals("m:o")) {
                // ignore for the moment
            } else {
                String type = spec.substring(0, 1);
                DataLayoutType baseType = getDataType(type);
                int[] values = getTypeWidths(spec);
                specs.add(new DataTypeSpecification(baseType, values));
                if (type.equals("n")) {
                    for (int value : values) {
                        addIfMissing(specs, new DataTypeSpecification(DataLayoutType.INTEGER, new int[]{value, value}));
                    }
                }
            }
        }
        // Add specs for 32 bit float and 64 bit double which are supported on all targets
        // http://releases.llvm.org/3.9.0/docs/LangRef.html#data-layout
        addIfMissing(specs, new DataTypeSpecification(DataLayoutType.FLOAT, new int[]{Float.SIZE, Float.SIZE}));
        addIfMissing(specs, new DataTypeSpecification(DataLayoutType.FLOAT, new int[]{Double.SIZE, Double.SIZE}));

        // FIXME:work around to handle pointer type in LLVM 3.9.0 bitcode format
        checkPointerType(specs);
        return specs;
    }

    private static void checkPointerType(List<DataTypeSpecification> specs) {
        boolean isPointerTypeFound = false;
        for (DataTypeSpecification spec : specs) {
            if (spec.type == DataLayoutType.POINTER) {
                isPointerTypeFound = true;
                break;
            }
        }
        if (!isPointerTypeFound) {
            // Add a pointer datatype with size = largest integer size
            int largestIntegerTypeSize = -1;
            for (DataTypeSpecification spec : specs) {
                if (spec.type == DataLayoutType.INTEGER && spec.values[0] > largestIntegerTypeSize) {
                    largestIntegerTypeSize = spec.values[0];
                }
            }
            if (largestIntegerTypeSize > 0) {
                specs.add(new DataTypeSpecification(DataLayoutType.POINTER, new int[]{largestIntegerTypeSize, largestIntegerTypeSize}));
            }
        }
    }

    private static int[] getTypeWidths(String spec) {
        String typeWidths = spec.substring(1);
        if (typeWidths.startsWith(":")) {
            typeWidths = typeWidths.substring(1);
        }
        String[] components = typeWidths.split(":");
        assertNoNullElement(components);
        int[] values = new int[components.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = Integer.parseInt(components[i]);
        }
        return values;
    }

    private static DataLayoutType getDataType(String string) {
        switch (string) {
            case "i":
                return DataLayoutType.INTEGER;
            case "f":
                return DataLayoutType.FLOAT;
            case "v":
                return DataLayoutType.VECTOR;
            case "p":
                return DataLayoutType.POINTER;
            case "a":
                return DataLayoutType.AGGREGATE;
            case "s":
                return DataLayoutType.STACK_OBJECT;
            case "S":
                return DataLayoutType.STACK;
            case "n":
                return DataLayoutType.INTEGER_WIDTHS;
            default:
                throw new AssertionError(string);
        }
    }

    private static Object[] assertNoNullElement(Object[] objects) {
        for (Object o : objects) {
            if (o == null) {
                throw new AssertionError(Arrays.toString(objects));
            }
        }
        return objects;
    }

}
