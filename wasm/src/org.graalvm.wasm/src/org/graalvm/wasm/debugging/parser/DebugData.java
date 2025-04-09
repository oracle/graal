/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.debugging.parser;

import org.graalvm.wasm.debugging.encoding.DataEncoding;

/**
 * Represents the data of an entry in the Dwarf Debug Information Format v4.
 */
public class DebugData {
    private final int tag;
    private final int offset;
    private final long[] attributeInfo;
    private final Object[] attributes;
    private final DebugData[] children;

    public DebugData(int tag, int offset, long[] attributeInfo, Object[] attributes, DebugData[] children) {
        assert attributes != null : "The attribute values of a debug data entry must not be null";
        assert children != null : "The children of a debug data entry must not be null";
        this.tag = tag;
        this.offset = offset;
        this.attributeInfo = attributeInfo;
        this.attributes = attributes;
        this.children = children;
    }

    public int tag() {
        return tag;
    }

    public int offset() {
        return offset;
    }

    public DebugData[] children() {
        return children;
    }

    private static int attributeValue(long a) {
        return (int) a;
    }

    private static int attributeEncoding(long a) {
        return (int) (a >> 32);
    }

    private int attributeIndex(int attribute) {
        for (int i = 0; i < attributeInfo.length; i++) {
            if (attributeValue(attributeInfo[i]) == attribute) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Converts the underlying attribute value to an integer.
     * 
     * @return the integer value or the given default value, if the attribute does not exist or the
     *         value does not fit into an integer.
     */
    public int asI32OrDefault(int attribute, int defaultValue) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return defaultValue;
        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        final int value;
        if (DataEncoding.isByte(encoding)) {
            value = (byte) attributes[index];
        } else if (DataEncoding.isShort(encoding)) {
            value = (short) attributes[index];
        } else if (DataEncoding.isInt(encoding)) {
            value = (int) attributes[index];
        } else {
            return defaultValue;
        }
        return value;
    }

    /**
     * Converts the underlying attribute value to an integer.
     * 
     * @return the integer valuer or {@link DebugUtil#DEFAULT_I32}, if the attribute does not exist
     *         or the value does not fit into an integer.
     */
    public int asI32OrDefault(int attribute) {
        return asI32OrDefault(attribute, DebugUtil.DEFAULT_I32);
    }

    /**
     * Converts the underlying attribute value to an integer. Interprets byte and short encodings as
     * unsigned integer values.
     * 
     * @return the integer value or the given default value, if the attribute does not exist or the
     *         value does not fit into an integer.
     */
    public int asU32OrDefault(int attribute, int defaultValue) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return defaultValue;
        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        final int value;
        if (DataEncoding.isByte(encoding)) {
            value = Byte.toUnsignedInt((byte) attributes[index]);
        } else if (DataEncoding.isShort(encoding)) {
            value = Short.toUnsignedInt((short) attributes[index]);
        } else if (DataEncoding.isInt(encoding)) {
            value = (int) attributes[index];
        } else {
            return defaultValue;
        }
        return value;
    }

    /**
     * Converts the underlying attribute value to an integer. Interprets byte and short encodings as
     * unsigned integer values.
     *
     * @return the integer value or {@link DebugUtil#DEFAULT_I32}, if the attribute does not exist
     *         or the value does not fit into an integer.
     */
    public int asU32OrDefault(int attribute) {
        return asU32OrDefault(attribute, DebugUtil.DEFAULT_I32);
    }

    /**
     * Converts the underlying attribute value to a long.
     *
     * @return the long value or the given default value, if the attribute does not exist or the
     *         value does not fit into a long.
     */
    public long asI64OrDefault(int attribute, long defaultValue) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return defaultValue;
        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        final long value;
        if (DataEncoding.isByte(encoding)) {
            value = (byte) attributes[index];
        } else if (DataEncoding.isShort(encoding)) {
            value = (short) attributes[index];
        } else if (DataEncoding.isInt(encoding)) {
            value = (int) attributes[index];
        } else if (DataEncoding.isLong(encoding)) {
            value = (long) attributes[index];
        } else {
            return defaultValue;
        }
        return value;
    }

    /**
     * Converts the underlying attribute value to a long.
     *
     * @return the long value or {@link DebugUtil#DEFAULT_I64}, if the attribute does not exist or
     *         the value does not fit into an integer.
     */
    public long asI64OrDefault(int attribute) {
        return asI64OrDefault(attribute, DebugUtil.DEFAULT_I64);
    }

    /**
     * Extracts the underlying attribute as a {@link String}.
     * 
     * @return the string or null, if the attribute does not exist or the value is not a
     *         {@link String}.
     */
    public String asStringOrNull(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return null;
        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        if (DataEncoding.isString(encoding)) {
            return (String) attributes[index];
        }
        return null;
    }

    /**
     * Extracts the underlying attribute as a {@link String}.
     * 
     * @return the string or an empty string, if the attribute does not exist or the value is not a
     *         {@link String}.
     */
    public String asStringOrEmpty(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return "";

        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        if (DataEncoding.isString(encoding)) {
            return (String) attributes[index];
        }
        return "";
    }

    /**
     * Extracts the underlying attribute as a byte array.
     *
     * @return the byte array or null, if the attribute does not exist or the value is not a byte
     *         array.
     */
    public byte[] asByteArrayOrNull(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return null;
        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        if (DataEncoding.isByteArray(encoding)) {
            return (byte[]) attributes[index];
        }
        return null;
    }

    /**
     * Returns <code>true</code> if the attribute is represented by a constant value, else
     * <code>false</code>.
     */
    public boolean isConstant(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return false;
        }
        return DataEncoding.isConstant(attributeEncoding(attributeInfo[index]));
    }

    /**
     * Returns <code>true</code> if the attribute is a boolean value and its value is
     * <code>true</code>, else <code>false</code>.
     */
    public boolean exists(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return false;
        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        if (DataEncoding.isBoolean(encoding)) {
            return (boolean) attributes[index];
        }
        return false;
    }

    /**
     * Returns <code>true</code> if the attribute is present, else <code>false</code>.
     */
    public boolean hasAttribute(int attribute) {
        return attributeIndex(attribute) != -1;
    }
}
