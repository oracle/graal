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

package org.graalvm.wasm.debugging.representation;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Represents a primitive expression needed to override values during debugging. Supports primitive
 * value expressions and strings.
 */
@ExportLibrary(InteropLibrary.class)
public class DebugPrimitiveValue implements TruffleObject {
    private final String expression;

    public DebugPrimitiveValue(String expression) {
        this.expression = expression;
    }

    @TruffleBoundary
    @ExportMessage
    public boolean isBoolean() {
        return "true".equalsIgnoreCase(expression) || "false".equalsIgnoreCase(expression);
    }

    @TruffleBoundary
    @ExportMessage
    public boolean asBoolean() {
        return Boolean.parseBoolean(expression);
    }

    @TruffleBoundary
    @ExportMessage
    public boolean isNumber() {
        // We return false here, so the debugger does not interpret this value as a number.
        // If it were a number, the chrome debugger would interpret floats as BigDecimal
        // values, which would lead to errors during debugging and would not allow us to change
        // floats and doubles.
        return false;
    }

    @TruffleBoundary
    @ExportMessage
    public boolean fitsInByte() {
        try {
            Byte.parseByte(expression);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public byte asByte() {
        try {
            return Byte.parseByte(expression);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public boolean fitsInShort() {
        try {
            Short.parseShort(expression);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public short asShort() {
        try {
            return Short.parseShort(expression);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public boolean fitsInInt() {
        try {
            Integer.parseInt(expression);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public int asInt() {
        try {
            return Integer.parseInt(expression);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public boolean fitsInLong() {
        try {
            Long.parseLong(expression);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public long asLong() {
        try {
            return Long.parseLong(expression);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public boolean fitsInFloat() {
        try {
            Float.parseFloat(expression);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public float asFloat() {
        try {
            return Float.parseFloat(expression);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public boolean fitsInDouble() {
        try {
            Double.parseDouble(expression);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public double asDouble() {
        try {
            return Double.parseDouble(expression);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public boolean fitsInBigInteger() {
        return false;
    }

    @TruffleBoundary
    @ExportMessage
    public BigInteger asBigInteger() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    @ExportMessage
    public boolean isString() {
        return !expression.isBlank();
    }

    @TruffleBoundary
    @ExportMessage
    public String asString() {
        return expression;
    }
}
