/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.webimage.api;

import java.math.BigInteger;

/**
 * Java representation of a JavaScript value.
 *
 * The subclasses of this class represent JavaScript's six primitive data types and the object data
 * type. The JavaScript {@code Null} data type does not have a special representation -- the
 * JavaScript {@code null} value is directly mapped to the Java {@code null} value.
 */
public abstract class JSValue {

    JSValue() {
    }

    public static JSUndefined undefined() {
        return JSUndefined.instance();
    }

    public abstract String typeof();

    protected abstract String stringValue();

    public Boolean asBoolean() {
        throw classCastError("Boolean");
    }

    public Byte asByte() {
        throw classCastError("Byte");
    }

    public Short asShort() {
        throw classCastError("Short");
    }

    public Character asChar() {
        throw classCastError("Character");
    }

    public Integer asInt() {
        throw classCastError("Integer");
    }

    public Float asFloat() {
        throw classCastError("Float");
    }

    public Long asLong() {
        throw classCastError("Long");
    }

    public Double asDouble() {
        throw classCastError("Double");
    }

    public BigInteger asBigInteger() {
        throw classCastError(BigInteger.class.getName());
    }

    public String asString() {
        throw classCastError("String");
    }

    public boolean[] asBooleanArray() {
        throw classCastError("boolean[]");
    }

    public byte[] asByteArray() {
        throw classCastError("byte[]");
    }

    public short[] asShortArray() {
        throw classCastError("short[]");
    }

    public char[] asCharArray() {
        throw classCastError("char[]");
    }

    public int[] asIntArray() {
        throw classCastError("int[]");
    }

    public float[] asFloatArray() {
        throw classCastError("float[]");
    }

    public long[] asLongArray() {
        throw classCastError("long[]");
    }

    public double[] asDoubleArray() {
        throw classCastError("double[]");
    }

    @SuppressWarnings("unchecked")
    public <T> T as(Class<T> cls) {
        if (cls.isAssignableFrom(this.getClass())) {
            return (T) this;
        }
        if (Number.class.isAssignableFrom(cls)) {
            // Dispatch to known numeric class casts.
            if (Integer.class.equals(cls)) {
                return (T) asInt();
            }
            if (Float.class.equals(cls)) {
                return (T) asFloat();
            }
            if (Long.class.equals(cls)) {
                return (T) asLong();
            }
            if (Double.class.equals(cls)) {
                return (T) asDouble();
            }
            if (Byte.class.equals(cls)) {
                return (T) asByte();
            }
            if (Short.class.equals(cls)) {
                return (T) asShort();
            }
            if (BigInteger.class.equals(cls)) {
                return (T) asBigInteger();
            }
        }
        if (String.class.equals(cls)) {
            return (T) asString();
        }
        if (cls.isArray() && cls.getComponentType().isPrimitive()) {
            // Dispatch to primitive array casts.
            if (int[].class.equals(cls)) {
                return (T) asIntArray();
            }
            if (float[].class.equals(cls)) {
                return (T) asFloatArray();
            }
            if (long[].class.equals(cls)) {
                return (T) asLongArray();
            }
            if (double[].class.equals(cls)) {
                return (T) asDoubleArray();
            }
            if (byte[].class.equals(cls)) {
                return (T) asByteArray();
            }
            if (short[].class.equals(cls)) {
                return (T) asShortArray();
            }
            if (char[].class.equals(cls)) {
                return (T) asCharArray();
            }
            if (boolean[].class.equals(cls)) {
                return (T) asBooleanArray();
            }
        }
        if (Character.class.equals(cls)) {
            return (T) asChar();
        }
        if (Boolean.class.equals(cls)) {
            return (T) asBoolean();
        }
        throw classCastError(cls.getName());
    }

    private ClassCastException classCastError(String type) {
        throw new ClassCastException("JavaScript '" + typeof() + "' value cannot be coerced to a Java '" + type + "'.");
    }

    @Override
    public String toString() {
        return "JavaScript<" + typeof() + "; " + stringValue() + ">";
    }
}
