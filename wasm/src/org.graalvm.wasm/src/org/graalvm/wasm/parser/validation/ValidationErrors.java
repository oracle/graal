/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.parser.validation;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.WasmType;

import java.util.StringJoiner;

public class ValidationErrors {
    private static String getValueTypeString(byte valueType) {
        switch (valueType) {
            case WasmType.VOID_TYPE:
                return "";
            case WasmType.I32_TYPE:
                return "i32";
            case WasmType.I64_TYPE:
                return "i64";
            case WasmType.F32_TYPE:
                return "f32";
            case WasmType.F64_TYPE:
                return "f64";
            case WasmType.V128_TYPE:
                return "v128";
            case WasmType.FUNCREF_TYPE:
                return "funcref";
            case WasmType.EXTERNREF_TYPE:
                return "externref";
        }
        return "unknown";
    }

    private static String getValueTypesString(byte[] valueTypes) {
        StringJoiner stringJoiner = new StringJoiner(",");
        for (byte valueType : valueTypes) {
            stringJoiner.add(getValueTypeString(valueType));
        }
        return stringJoiner.toString();
    }

    private static WasmException create(String message, Object expected, Object actual) {
        return WasmException.format(Failure.TYPE_MISMATCH, message, expected, actual);
    }

    @TruffleBoundary
    public static WasmException createTypeMismatch(byte expectedType, byte actualType) {
        String expectedTypeString = getValueTypeString(expectedType);
        String actualTypeString = getValueTypeString(actualType);
        return create("Expected type [%s], but got [%s].", expectedTypeString, actualTypeString);
    }

    @TruffleBoundary
    public static WasmException createResultTypesMismatch(byte[] expectedTypes, byte[] actualTypes) {
        String expectedTypesString = getValueTypesString(expectedTypes);
        String actualTypesString = getValueTypesString(actualTypes);
        return create("Expected result types [%s], but got [%s].", expectedTypesString, actualTypesString);
    }

    @TruffleBoundary
    public static WasmException createLabelTypesMismatch(byte[] expectedTypes, byte[] actualTypes) {
        String expectedTypesString = getValueTypesString(expectedTypes);
        String actualTypesString = getValueTypesString(actualTypes);
        return create("Inconsistent label types. Expected [%s], but got [%s].", expectedTypesString, actualTypesString);
    }

    @TruffleBoundary
    public static WasmException createParamTypesMismatch(byte[] expectedTypes, byte[] actualTypes) {
        String expectedTypesString = getValueTypesString(expectedTypes);
        String actualTypesString = getValueTypesString(actualTypes);
        return create("Expected param types [%s], but got [%s].", expectedTypesString, actualTypesString);
    }

    @TruffleBoundary
    public static WasmException createMissingLabel(int expected, int max) {
        return WasmException.format(Failure.UNKNOWN_LABEL, "Unknown branch label %d (max %d).", expected, max);
    }

    @TruffleBoundary
    public static WasmException createMissingFunctionType(int expected, int max) {
        return WasmException.format(Failure.UNKNOWN_TYPE, "Function type variable %d out of range. (max %d)", expected, max);
    }

    @TruffleBoundary
    public static WasmException createExpectedAnyOnEmptyStack() {
        return WasmException.create(Failure.TYPE_MISMATCH, "Expected type [any], but got [].");
    }

    @TruffleBoundary
    public static WasmException createExpectedTypeOnEmptyStack(byte expectedType) {
        String expectedTypeString = getValueTypeString(expectedType);
        return create("Expected type [%s], but got [].", expectedTypeString, "");
    }
}
