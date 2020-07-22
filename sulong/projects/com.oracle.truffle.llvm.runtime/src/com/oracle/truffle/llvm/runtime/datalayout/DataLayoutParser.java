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

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

final class DataLayoutParser {

    static final class DataTypeSpecification {

        private final DataLayoutType type;
        private final int[] values;

        private DataTypeSpecification(DataLayoutType type, int size, int abiAlignment, int preferredAlignment) {
            assert type == DataLayoutType.INTEGER || type == DataLayoutType.POINTER || type == DataLayoutType.FLOAT;
            this.type = type;
            this.values = new int[]{size, abiAlignment, preferredAlignment};
        }

        private DataTypeSpecification(DataLayoutType type, int[] values) {
            assert type == DataLayoutType.INTEGER_WIDTHS;
            this.type = type;
            this.values = values;
        }

        DataLayoutType getType() {
            return type;
        }

        int[] getValues() {
            assert type == DataLayoutType.INTEGER_WIDTHS;
            return values;
        }

        int getSize() {
            assert type == DataLayoutType.INTEGER || type == DataLayoutType.POINTER || type == DataLayoutType.FLOAT;
            return values[0];
        }

        int getAbiAlignment() {
            assert type == DataLayoutType.INTEGER || type == DataLayoutType.POINTER || type == DataLayoutType.FLOAT;
            return values[1];
        }

        int getPreferredAlignment() {
            assert type == DataLayoutType.INTEGER || type == DataLayoutType.POINTER || type == DataLayoutType.FLOAT;
            return values[2];
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj instanceof DataTypeSpecification) {
                DataTypeSpecification other = (DataTypeSpecification) obj;
                return this.type == other.type && Arrays.equals(this.values, other.values);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return type.hashCode() + Arrays.hashCode(values);
        }

        @Override
        public String toString() {
            return getType() + " " + Arrays.toString(values);
        }
    }

    private static void addIfMissing(List<DataTypeSpecification> specs, DataTypeSpecification newSpec) {
        assert newSpec.type == DataLayoutType.INTEGER || newSpec.type == DataLayoutType.POINTER || newSpec.type == DataLayoutType.FLOAT;
        for (DataTypeSpecification spec : specs) {
            if (spec.type == newSpec.type && spec.getSize() == newSpec.getSize()) {
                return;
            }
        }
        specs.add(newSpec);
    }

    /**
     * Parses the LLVM data layout string.
     * 
     * @see <a href="https://llvm.org/docs/LangRef.html#data-layout">Data Layout</a>
     * @param layout The data layout string
     * @param specs list to collect data type specifications
     * @return the byte order specified by the data layout
     */
    static ByteOrder parseDataLayout(String layout, List<DataTypeSpecification> specs) {
        /* According to the LLVM documentation, big endian is the default. */
        ByteOrder byteOrder = BIG_ENDIAN;
        String[] layoutSpecs = layout.split("-");
        for (String spec : layoutSpecs) {
            if (spec.equals("E")) {
                byteOrder = BIG_ENDIAN;
                continue;
            }
            if (spec.equals("e")) {
                byteOrder = LITTLE_ENDIAN;
                continue;
            }
            // at the moment, we are only interested in a small subset of all identifiers
            DataLayoutType type = getDataType(spec);
            DataTypeSpecification dataTypeSpec = createDataTypeSpec(type, spec);
            if (dataTypeSpec != null) {
                specs.add(dataTypeSpec);
            }
        }

        // Add specs for all native integer widths
        for (int i = 0; i < specs.size(); i++) {
            DataTypeSpecification spec = specs.get(i);
            if (spec.getType() == DataLayoutType.INTEGER_WIDTHS) {
                for (int value : spec.getValues()) {
                    addIfMissing(specs, new DataTypeSpecification(DataLayoutType.INTEGER, value, value, value));
                }
            }
        }

        // Add specs for 32 bit float and 64 bit double which are supported on all targets
        // http://releases.llvm.org/3.9.0/docs/LangRef.html#data-layout
        addIfMissing(specs, new DataTypeSpecification(DataLayoutType.FLOAT, Float.SIZE, Float.SIZE, Float.SIZE));
        addIfMissing(specs, new DataTypeSpecification(DataLayoutType.FLOAT, Double.SIZE, Double.SIZE, Double.SIZE));

        // FIXME:work around to handle pointer type in LLVM 3.9.0 bitcode format
        checkPointerType(specs);
        return byteOrder;
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
                if (spec.type == DataLayoutType.INTEGER && spec.getSize() > largestIntegerTypeSize) {
                    largestIntegerTypeSize = spec.getSize();
                }
            }
            if (largestIntegerTypeSize > 0) {
                specs.add(new DataTypeSpecification(DataLayoutType.POINTER, largestIntegerTypeSize, largestIntegerTypeSize, largestIntegerTypeSize));
            }
        }
    }

    private static DataTypeSpecification createDataTypeSpec(DataLayoutType type, String spec) {
        String[] components = spec.split(":");
        // remove the type prefix
        components[0] = components[0].substring(1);

        if (type == DataLayoutType.INTEGER || type == DataLayoutType.FLOAT) {
            assert components.length >= 1;
            int size = convertToInt(components, 0);
            int abiAlignment = convertToInt(components, 1, size);
            int preferredAlignment = convertToInt(components, 2, abiAlignment);
            return new DataTypeSpecification(type, size, abiAlignment, preferredAlignment);
        } else if (type == DataLayoutType.POINTER) {
            assert components.length >= 2;
            int size = convertToInt(components, 1);
            int abiAlignment = convertToInt(components, 2, size);
            int preferredAlignment = convertToInt(components, 3, abiAlignment);
            return new DataTypeSpecification(type, size, abiAlignment, preferredAlignment);
        } else if (type == DataLayoutType.INTEGER_WIDTHS) {
            return new DataTypeSpecification(type, convertToInt(components));
        } else {
            return null;
        }
    }

    private static int[] convertToInt(String[] components) {
        int[] values = new int[components.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = convertToInt(components, i);
        }
        return values;
    }

    private static int convertToInt(String[] components, int index) {
        return Integer.parseInt(components[index]);
    }

    private static int convertToInt(String[] components, int index, int defaultValue) {
        if (index >= components.length) {
            return defaultValue;
        } else {
            return Integer.parseInt(components[index]);
        }
    }

    private static DataLayoutType getDataType(String spec) {
        if (spec.startsWith("i")) {
            return DataLayoutType.INTEGER;
        } else if (spec.startsWith("f")) {
            return DataLayoutType.FLOAT;
        } else if (spec.startsWith("p")) {
            return DataLayoutType.POINTER;
        } else if (spec.startsWith("n") && !spec.startsWith("ni")) {
            return DataLayoutType.INTEGER_WIDTHS;
        } else {
            return null;
        }
    }
}
