/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm;

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings({"unused", "static-method"})
public class WasmType implements TruffleObject {
    public static final byte VOID_TYPE = 0x40;

    public static final byte I32_TYPE = 0x7F;

    public static final byte I64_TYPE = 0x7E;

    public static final byte F32_TYPE = 0x7D;

    public static final byte F64_TYPE = 0x7C;

    public static final WasmType VOID = new WasmType("void");

    public static String toString(int valueType) {
        CompilerAsserts.neverPartOfCompilation();
        switch (valueType) {
            case I32_TYPE:
                return "i32";
            case I64_TYPE:
                return "i64";
            case F32_TYPE:
                return "f32";
            case F64_TYPE:
                return "f64";
            case VOID_TYPE:
                return "void";
            default:
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, null, "Unknown value type: 0x" + Integer.toHexString(valueType));
        }
    }

    private final String name;

    public WasmType(String name) {
        this.name = name;
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return WasmLanguage.class;
    }

    @ExportMessage
    boolean isMetaObject() {
        return true;
    }

    @ExportMessage(name = "getMetaQualifiedName")
    @ExportMessage(name = "getMetaSimpleName")
    public Object getName() {
        return name;
    }

    @ExportMessage(name = "toDisplayString")
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return toString();
    }

    @ExportMessage
    final boolean isMetaInstance(Object instance) throws UnsupportedMessageException {
        return instance instanceof WasmVoidResult;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return "wasm-value-type[" + name + "]";
    }

    public static FrameSlotKind asFrameSlotKind(byte type) {
        CompilerAsserts.neverPartOfCompilation();
        switch (type) {
            case I32_TYPE:
                return FrameSlotKind.Int;
            case I64_TYPE:
                return FrameSlotKind.Long;
            case F32_TYPE:
                return FrameSlotKind.Float;
            case F64_TYPE:
                return FrameSlotKind.Double;
            default:
                return null;
        }
    }
}
