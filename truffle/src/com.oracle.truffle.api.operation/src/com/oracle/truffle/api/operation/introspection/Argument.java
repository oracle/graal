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
package com.oracle.truffle.api.operation.introspection;

import java.util.Arrays;

public final class Argument {

    private final Object[] data;

    Argument(Object[] data) {
        this.data = data;
    }

    public enum ArgumentKind {
        LOCAL,
        ARGUMENT,
        BOXING,
        CONSTANT,
        CHILD_OFFSET,
        VARIADIC,
        BRANCH_OFFSET;

        public String toString(Object value) {
            switch (this) {
                case LOCAL:
                    return String.format("local(%d)", (short) value);
                case ARGUMENT:
                    return String.format("arg(%d)", (short) value);
                case BOXING:
                    return String.format("boxing(%s)", value);
                case CONSTANT:
                    return String.format("const(%s)", printConstant(value));
                case CHILD_OFFSET:
                    return String.format("child(-%d)", (short) value);
                case VARIADIC:
                    return String.format("variadic(%d)", (short) value);
                case BRANCH_OFFSET:
                    return String.format("branch(%04x)", (short) value);
                default:
                    throw new UnsupportedOperationException("Unexpected value: " + this);
            }
        }

        private static String printConstant(Object value) {
            if (value == null) {
                return "null";
            }
            String typeString = value.getClass().getSimpleName();
            String valueString = value.getClass().isArray() ? printArray(value) : value.toString();
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
    }

    public ArgumentKind getKind() {
        return (ArgumentKind) data[0];
    }

    public Object getValue() {
        return data[1];
    }

    @Override
    public String toString() {
        return getKind().toString(getValue());
    }

}
