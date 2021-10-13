/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.runtime;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.graalvm.wasm.WasmType;

import java.util.Arrays;

public class WasmFunctionTypeStore {
    private static final int INITIAL_FUNCTION_TYPE_COUNT = 16;

    @CompilationFinal(dimensions = 1) private WasmFunctionType[] functionTypes = new WasmFunctionType[INITIAL_FUNCTION_TYPE_COUNT];
    private int functionTypeCount = 0;

    private void ensureCapacity() {
        if (functionTypeCount == functionTypes.length) {
            final WasmFunctionType[] updatedFunctionTypes = new WasmFunctionType[functionTypes.length * 2];
            System.arraycopy(functionTypes, 0, updatedFunctionTypes, 0, functionTypes.length);
            functionTypes = updatedFunctionTypes;
        }
    }

    public WasmFunctionType getFunctionType(byte[] parameterTypes, byte[] returnTypes) {
        final byte returnType;
        if (returnTypes.length == 0) {
            returnType = WasmType.VOID_TYPE;
        } else {
            returnType = returnTypes[0];
        }
        return getFunctionType(parameterTypes, returnType);
    }

    public WasmFunctionType getFunctionType(byte[] parameterTypes, byte returnType) {
        for (WasmFunctionType f : functionTypes) {
            if (f != null && f.getReturnType() == returnType && Arrays.equals(f.getParameterTypes(), parameterTypes)) {
                return f;
            }
        }
        ensureCapacity();
        WasmFunctionType f = new WasmFunctionType(functionTypeCount, parameterTypes, returnType);
        functionTypes[functionTypeCount] = f;
        functionTypeCount++;
        return f;
    }

    public WasmFunctionType getFunctionType(int index) {
        assert Integer.compareUnsigned(index, functionTypes.length) < 0;
        return functionTypes[index];
    }
}
