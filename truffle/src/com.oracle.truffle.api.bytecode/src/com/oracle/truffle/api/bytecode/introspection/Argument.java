/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.introspection;

import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.nodes.Node;

public final class Argument {

    private final Object[] data;
    private final List<SpecializationInfo> specializationInfo;

    Argument(Object[] data) {
        this.data = data;
        assert data.length >= 3;

        // materialize eagerly to materialize for possible mutations
        this.specializationInfo = materializeSpecializationInfo();
    }

    private List<SpecializationInfo> materializeSpecializationInfo() {
        if (getKind() != ArgumentType.NODE_PROFILE) {
            return null;
        }
        Object o = data[2];
        if (o instanceof Node n && Introspection.isIntrospectable(n)) {
            return Introspection.getSpecializations(n);
        } else {
            return null;
        }
    }

    public enum ArgumentType {
        CONSTANT,
        BYTECODE_INDEX,
        INTEGER,
        NODE_PROFILE,
        BRANCH_PROFILE,
        INSTRUMENT_PROFILE;
    }

    public ArgumentType getKind() {
        return (ArgumentType) data[0];
    }

    public int getInteger() {
        if (getKind() != ArgumentType.CONSTANT) {
            throw new UnsupportedOperationException(String.format("Not supported for argument type %s.", getKind()));
        }
        return (short) data[2];
    }

    public int getBytecodeIndex() {
        if (getKind() != ArgumentType.CONSTANT) {
            throw new UnsupportedOperationException(String.format("Not supported for argument type %s.", getKind()));
        }
        return (short) data[2];
    }

    public Object getConstant() {
        if (getKind() != ArgumentType.CONSTANT) {
            throw new UnsupportedOperationException(String.format("Not supported for argument type %s.", getKind()));
        }
        return data[2];
    }

    public Node getNode() {
        if (getKind() != ArgumentType.NODE_PROFILE) {
            throw new UnsupportedOperationException(String.format("Not supported for argument type %s.", getKind()));
        }
        Object o = data[2];
        if (o instanceof Node n) {
            return n;
        } else {
            return null;
        }
    }

    public List<SpecializationInfo> getSpecializationInfo() {
        if (getKind() != ArgumentType.NODE_PROFILE) {
            throw new UnsupportedOperationException(String.format("Not supported for argument type %s.", getKind()));
        }
        return specializationInfo;
    }

    public BranchProfile getBranchProfile() {
        if (getKind() != ArgumentType.BRANCH_PROFILE) {
            throw new UnsupportedOperationException(String.format("Not supported for argument type %s.", getKind()));
        }
        int[] values = (int[]) data[2];
        return new BranchProfile(values[0], values[1], values[2]);
    }

    public String getName() {
        return (String) data[1];
    }

    @Override
    public String toString() {
        switch (getKind()) {
            case INTEGER:
                return String.format("%s(%d)", getName(), (short) data[2]);
            case CONSTANT:
                return String.format("%s(%s)", getName(), printConstant(data[2]));
            case NODE_PROFILE:
                return String.format("%s(%s)", getName(), printNodeProfile(data[2]));
            case BYTECODE_INDEX:
                return String.format("%s(%04x)", getName(), (short) data[2]);
            case BRANCH_PROFILE:
                return String.format("%s(%s)", getName(), getBranchProfile().toString());
            case INSTRUMENT_PROFILE:
                return String.format("%s(%s)", getName(), printInstrumentationProfile(data[2]));
            default:
                throw new UnsupportedOperationException("Unexpected value: " + this);
        }
    }

    private static String printInstrumentationProfile(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString();
    }

    private String printNodeProfile(Object o) {
        StringBuilder sb = new StringBuilder();
        if (o == null) {
            return "null";
        }
        sb.append(o.getClass().getSimpleName());
        List<SpecializationInfo> info = getSpecializationInfo();
        if (info != null) {
            sb.append("(");
            String sep = "";
            for (SpecializationInfo specialization : info) {
                if (specialization.getInstances() == 0) {
                    continue;
                }
                sb.append(sep);
                sb.append(specialization.getMethodName());
                sep = "#";
            }
            sb.append(")");
        }
        return sb.toString();
    }

    private static String printConstant(Object value) {
        if (value == null) {
            return "null";
        }
        String typeString = value.getClass().getSimpleName();
        String valueString = value.getClass().isArray() ? printArray(value) : value.toString();
        if (valueString.length() > 100) {
            valueString = valueString.substring(0, 97) + "...";
        }
        return String.format("%s %s", typeString, valueString);
    }

    private static String printArray(Object array) {
        if (array instanceof Object[] objArr) {
            return Arrays.toString(objArr);
        } else if (array instanceof long[] longArr) {
            return Arrays.toString(longArr);
        } else if (array instanceof int[] intArr) {
            return Arrays.toString(intArr);
        } else if (array instanceof short[] shortArr) {
            return Arrays.toString(shortArr);
        } else if (array instanceof char[] charArr) {
            return Arrays.toString(charArr);
        } else if (array instanceof byte[] byteArr) {
            return Arrays.toString(byteArr);
        } else if (array instanceof double[] doubleArr) {
            return Arrays.toString(doubleArr);
        } else if (array instanceof float[] floatArr) {
            return Arrays.toString(floatArr);
        } else if (array instanceof boolean[] boolArr) {
            return Arrays.toString(boolArr);
        }
        throw new AssertionError(String.format("Unhandled array type %s", array));
    }

    public record BranchProfile(int index, int trueCount, int falseCount) {

        public double getFrequency() {
            int total = trueCount + falseCount;
            if (total == 0) {
                return 0.0d;
            }
            return ((double) trueCount) / ((double) total);
        }

        @Override
        public String toString() {
            if (trueCount + falseCount == 0) {
                return index + ":never executed";
            }
            return String.format("%s:%.2f", index, getFrequency());
        }

    }

}
