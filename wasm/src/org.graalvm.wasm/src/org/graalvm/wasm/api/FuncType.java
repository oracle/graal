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

package org.graalvm.wasm.api;

import org.graalvm.wasm.SymbolTable;
import org.graalvm.wasm.exception.WasmJsApiException;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Represents the type of functions and exceptions in the JS API.
 */
public final class FuncType {
    private static final ValueType[] EMTPY = {};

    @CompilationFinal(dimensions = 1) private final ValueType[] params;
    @CompilationFinal(dimensions = 1) private final ValueType[] results;

    private FuncType(ValueType[] params, ValueType[] results) {
        this.params = params;
        this.results = results;
    }

    public static FuncType fromString(String s) {
        final int leftPar = s.indexOf('(');
        final int rightPar = s.indexOf(')');
        if (leftPar == -1 || rightPar == -1) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid function type format");
        }
        final ValueType[] params = parseTypeString(s, leftPar + 1, rightPar);
        final ValueType[] results = parseTypeString(s, rightPar + 1, s.length());
        return new FuncType(params, results);
    }

    private static ValueType[] parseTypeString(String typesString, int start, int end) {
        if (start >= end) {
            return EMTPY;
        } else {
            String[] typeNames = typesString.substring(start, end).split(" ");
            ValueType[] types = new ValueType[typeNames.length];
            for (int i = 0; i < typeNames.length; i++) {
                types[i] = ValueType.valueOf(typeNames[i]);
            }
            return types;
        }
    }

    public static FuncType fromFunctionType(SymbolTable.FunctionType functionType) {
        final int[] paramTypes = functionType.paramTypes();
        final int[] resultTypes = functionType.resultTypes();

        final ValueType[] params = new ValueType[paramTypes.length];
        final ValueType[] results = new ValueType[resultTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = ValueType.fromValue(paramTypes[i]);
        }
        for (int i = 0; i < resultTypes.length; i++) {
            results[i] = ValueType.fromValue(resultTypes[i]);
        }
        return new FuncType(params, results);
    }

    public ValueType paramTypeAt(int index) {
        return params[index];
    }

    public int paramCount() {
        return params.length;
    }

    public ValueType resultTypeAt(int index) {
        return results[index];
    }

    public int resultCount() {
        return results.length;
    }

    public SymbolTable.FunctionType toFunctionType() {
        final int[] paramTypes = new int[params.length];
        final int[] resultTypes = new int[results.length];

        for (int i = 0; i < paramTypes.length; i++) {
            paramTypes[i] = params[i].value();
        }
        for (int i = 0; i < resultTypes.length; i++) {
            resultTypes[i] = results[i].value();
        }
        return SymbolTable.FunctionType.createClosed(paramTypes, resultTypes);
    }

    public String toString(StringBuilder b) {
        b.append("(");
        for (int i = 0; i < params.length; i++) {
            if (i != 0) {
                b.append(' ');
            }
            b.append(params[i]);
        }
        b.append(')');
        for (int i = 0; i < results.length; i++) {
            if (i != 0) {
                b.append(' ');
            }
            b.append(results[i]);
        }
        return b.toString();
    }

    @Override
    public String toString() {
        return toString(new StringBuilder());
    }
}
