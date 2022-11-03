/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import org.graalvm.wasm.debugging.WasmDebugException;
import org.graalvm.wasm.debugging.encoding.DataEncoding;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class DebugData {
    private final int tag;
    private final int offset;
    @CompilationFinal(dimensions = 1) private final long[] attributeInfo;
    @CompilationFinal(dimensions = 1) private final Object[] attributes;
    @CompilationFinal(dimensions = 1) private final DebugData[] children;

    public DebugData(int tag, int offset, long[] attributeInfo, Object[] attributes, DebugData[] children) {
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

    private int attributeValue(long attributeInfo) {
        return (int) attributeInfo;
    }

    private int attributeEncoding(long attributeInfo) {
        return (int) (attributeInfo >> 32);
    }

    private int attributeIndex(int attribute) {
        for (int i = 0; i < attributeInfo.length; i++) {
            if (attributeValue(attributeInfo[i]) == attribute) {
                return i;
            }
        }
        return -1;
    }

    public int asI32(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            throw new WasmDebugException(String.format("Debug entry %d does not contain attribute %d", tag, attribute));
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
            throw new WasmDebugException(String.format("Debug entry %d attribute encoding %d not supported", tag, encoding));
        }
        return value;
    }

    public Optional<Integer> tryAsI32(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return Optional.empty();
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
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public long asI64(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            throw new WasmDebugException(String.format("Debug entry %d does not contain attribute %d", tag, attribute));
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
            throw new WasmDebugException(String.format("Debug entry %d attribute encoding %d not supported", tag, encoding));
        }
        return value;
    }

    public Optional<Long> tryAsI64(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return Optional.empty();
        }
        final int attributeEncoding = attributeEncoding(attributeInfo[index]);
        final long value;
        if (DataEncoding.isByte(attributeEncoding)) {
            value = (byte) attributes[index];
        } else if (DataEncoding.isShort(attributeEncoding)) {
            value = (short) attributes[index];
        } else if (DataEncoding.isInt(attributeEncoding)) {
            value = (int) attributes[index];
        } else if (DataEncoding.isLong(attributeEncoding)) {
            value = (long) attributes[index];
        } else {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public String asString(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            throw new WasmDebugException(String.format("Debug entry %d does not contain attribute %d", tag, attribute));
        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        if (DataEncoding.isString(encoding)) {
            return (String) attributes[index];
        }
        throw new WasmDebugException(String.format("Debug entry %d attribute encoding %d not supported", tag, encoding));
    }

    public Optional<String> tryAsString(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return Optional.empty();
        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        if (DataEncoding.isString(encoding)) {
            return Optional.of((String) attributes[index]);
        }
        return Optional.empty();
    }

    public byte[] asByteArray(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            throw new WasmDebugException(String.format("Debug entry %d does not contain attribute %d", tag, attribute));
        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        if (DataEncoding.isByteArray(encoding)) {
            return (byte[]) attributes[index];
        }
        throw new WasmDebugException(String.format("Debug entry %d attribute encoding %d not supported", tag, encoding));
    }

    public Optional<byte[]> tryAsByteArray(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return Optional.empty();
        }
        final int encoding = attributeEncoding(attributeInfo[index]);
        if (DataEncoding.isByteArray(encoding)) {
            return Optional.of((byte[]) attributes[index]);
        }
        return Optional.empty();
    }

    public boolean isConstant(int attribute) {
        final int index = attributeIndex(attribute);
        if (index == -1) {
            return false;
        }
        return DataEncoding.isConstant(attributeEncoding(attributeInfo[index]));
    }

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
}
