/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
