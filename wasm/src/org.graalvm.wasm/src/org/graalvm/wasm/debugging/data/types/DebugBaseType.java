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

package org.graalvm.wasm.debugging.data.types;

import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.DebugContext;
import org.graalvm.wasm.debugging.data.DebugType;
import org.graalvm.wasm.debugging.encoding.AttributeEncodings;
import org.graalvm.wasm.debugging.representation.DebugConstantDisplayValue;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

/**
 * Represents a debug type that is a base type like int or float.
 */
public class DebugBaseType extends DebugType {
    private final String typeName;
    private final int encoding;
    private final int byteSize;
    private final int bitSize;
    private final int bitOffset;

    public DebugBaseType(String typeName, int encoding, int byteSize, int bitSize, int bitOffset) {
        this.typeName = typeName;
        this.encoding = encoding;
        this.byteSize = byteSize;
        this.bitSize = bitSize;
        this.bitOffset = bitOffset;
    }

    private static String toZeroTerminatedString(DebugLocation location) {
        final StringBuilder builder = new StringBuilder();
        DebugLocation l = location;
        for (int i = 0; i < 255; i++) {
            final byte b = l.loadI8();
            if (b == 0) {
                break;
            }
            builder.append((char) b);
            l = l.nextByte();
        }
        return builder.toString();
    }

    @Override
    public String asTypeName() {
        return typeName;
    }

    @Override
    public int valueLength() {
        return byteSize;
    }

    @Override
    public boolean isValue() {
        return true;
    }

    @Override
    public Object asValue(DebugContext context, DebugLocation location) {
        final int effectiveBitSize = context.memberBitSizeOrDefault(bitSize);
        final int effectiveBitOffset = context.memberBitOffsetOrDefault(bitOffset);
        if (encoding == AttributeEncodings.ADDRESS) {
            return location.loadAsLocation();
        }
        if (encoding == AttributeEncodings.BOOLEAN) {
            return location.loadI8(effectiveBitSize, effectiveBitOffset) != 0;
        }
        if (encoding == AttributeEncodings.FLOAT) {
            if (byteSize == 4) {
                return location.loadF32();
            }
            if (byteSize == 8) {
                return location.loadF64();
            }
        }
        if (encoding == AttributeEncodings.SIGNED) {
            if (byteSize == 1) {
                return location.loadI8(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 2) {
                return location.loadI16(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 4) {
                return location.loadI32(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 8) {
                return location.loadI64(effectiveBitSize, effectiveBitOffset);
            }
        }
        if (encoding == AttributeEncodings.UNSIGNED) {
            if (byteSize == 1) {
                return location.loadU8(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 2) {
                return location.loadU16(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 4) {
                return location.loadU32(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 8) {
                return new DebugConstantDisplayValue(location.loadU64(effectiveBitSize, effectiveBitOffset));
            }
        }
        if (encoding == AttributeEncodings.SIGNED_CHAR) {
            final byte byteValue = location.loadI8(effectiveBitSize, effectiveBitOffset);
            return new DebugConstantDisplayValue("'" + (char) byteValue + "' " + byteValue);
        }
        if (encoding == AttributeEncodings.UNSIGNED_CHAR) {
            final int byteValue = location.loadU8(effectiveBitSize, effectiveBitOffset);
            return new DebugConstantDisplayValue("'" + (char) byteValue + "' " + byteValue);
        }
        if (encoding == AttributeEncodings.UTF) {
            return toZeroTerminatedString(location);
        }
        return DebugConstantDisplayValue.UNSUPPORTED;
    }

    @Override
    public boolean isModifiableValue() {
        return encoding == AttributeEncodings.BOOLEAN ||
                        encoding == AttributeEncodings.FLOAT ||
                        encoding == AttributeEncodings.SIGNED ||
                        (encoding == AttributeEncodings.UNSIGNED && byteSize <= 4) ||
                        encoding == AttributeEncodings.SIGNED_CHAR ||
                        encoding == AttributeEncodings.UNSIGNED_CHAR;
    }

    @Override
    public void setValue(DebugContext context, DebugLocation location, Object value, InteropLibrary lib) {
        if (!isModifiableValue()) {
            return;
        }
        final int effectiveBitSize = context.memberBitSizeOrDefault(bitSize);
        try {
            if (encoding == AttributeEncodings.BOOLEAN) {
                if (lib.isBoolean(value)) {
                    location.store8(effectiveBitSize, (byte) (lib.asBoolean(value) ? 1 : 0));
                }
            }
            if (encoding == AttributeEncodings.FLOAT) {
                if (byteSize == 4 && lib.fitsInFloat(value)) {
                    location.storeF32(lib.asFloat(value));
                }
                if (byteSize == 8 && lib.fitsInDouble(value)) {
                    location.storeF64(lib.asDouble(value));
                }
            }
            if (encoding == AttributeEncodings.SIGNED || encoding == AttributeEncodings.UNSIGNED) {
                if (byteSize == 1 && lib.fitsInByte(value)) {
                    location.store8(effectiveBitSize, lib.asByte(value));
                }
                if (byteSize == 2 && lib.fitsInShort(value)) {
                    location.store16(effectiveBitSize, lib.asShort(value));
                }
                if (byteSize == 4 && lib.fitsInInt(value)) {
                    location.store32(effectiveBitSize, lib.asInt(value));
                }
                if (byteSize == 8 && lib.fitsInLong(value)) {
                    location.store64(effectiveBitSize, lib.asLong(value));
                }
            }
            if (encoding == AttributeEncodings.SIGNED_CHAR || encoding == AttributeEncodings.UNSIGNED_CHAR) {
                if (lib.fitsInByte(value)) {
                    location.store8(effectiveBitSize, lib.asByte(value));
                } else if (lib.isString(value)) {
                    location.store8(effectiveBitSize, (byte) lib.asString(value).charAt(0));
                }
            }
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @Override
    public boolean fitsIntoInt() {
        if (encoding == AttributeEncodings.SIGNED) {
            return byteSize <= 4;
        }
        if (encoding == AttributeEncodings.UNSIGNED) {
            return byteSize <= 2;
        }
        return false;
    }

    @Override
    public int asInt(DebugContext context, DebugLocation location) {
        final int effectiveBitSize = context.memberBitSizeOrDefault(bitSize);
        final int effectiveBitOffset = context.memberBitOffsetOrDefault(bitOffset);
        if (encoding == AttributeEncodings.SIGNED) {
            if (byteSize == 1) {
                return location.loadI8(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 2) {
                return location.loadI16(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 4) {
                return location.loadI32(effectiveBitSize, effectiveBitOffset);
            }
        }
        if (encoding == AttributeEncodings.UNSIGNED) {
            if (byteSize == 1) {
                return location.loadU8(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 2) {
                return location.loadU16(effectiveBitSize, effectiveBitOffset);
            }
        }
        return 0;
    }

    @Override
    public boolean fitsIntoLong() {
        if (encoding == AttributeEncodings.SIGNED) {
            return byteSize <= 8;
        }
        if (encoding == AttributeEncodings.UNSIGNED) {
            return byteSize <= 4;
        }
        return false;
    }

    @Override
    public long asLong(DebugContext context, DebugLocation location) {
        final int effectiveBitSize = context.memberBitSizeOrDefault(bitSize);
        final int effectiveBitOffset = context.memberBitOffsetOrDefault(bitOffset);
        if (encoding == AttributeEncodings.SIGNED) {
            if (byteSize == 1) {
                return location.loadI8(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 2) {
                return location.loadI16(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 4) {
                return location.loadI32(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 8) {
                return location.loadI64(effectiveBitSize, effectiveBitOffset);
            }
        }
        if (encoding == AttributeEncodings.UNSIGNED) {
            if (byteSize == 1) {
                return location.loadU8(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 2) {
                return location.loadU16(effectiveBitSize, effectiveBitOffset);
            }
            if (byteSize == 4) {
                return location.loadU32(effectiveBitSize, effectiveBitOffset);
            }
        }
        return 0L;
    }
}
