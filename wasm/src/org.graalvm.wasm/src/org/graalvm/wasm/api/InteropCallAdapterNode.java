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
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.nodes.WasmIndirectCallNode;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;

/**
 * Wrapper call target for executing wasm functions, validating and adapting the arguments for wasm
 * and converting the return value for interop.
 * <p>
 * Expected input arguments: {@code (WasmFunctionInstance, ...interop_arguments)}.<br>
 * Adapted to call arguments: {@code (WasmInstance, ...wasm_arguments)}.
 * <p>
 * The adapter call target is specialized per function type signature, not per function. This allows
 * the call target to be reused by different functions of the same type (equivalence class).
 */
public final class InteropCallAdapterNode extends RootNode {

    private static final int MAX_UNROLL = 32;

    private final SymbolTable.FunctionType functionType;
    private final BranchProfile errorBranch = BranchProfile.create();
    @Child private WasmIndirectCallNode callNode;

    public InteropCallAdapterNode(WasmLanguage language, SymbolTable.FunctionType functionType) {
        super(language);
        this.functionType = functionType;
        this.callNode = WasmIndirectCallNode.create();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final WasmFunctionInstance functionInstance = (WasmFunctionInstance) frame.getArguments()[0];

        Object[] arguments = frame.getArguments();
        WasmArguments.setModuleInstance(arguments, functionInstance.moduleInstance());

        try {
            assert functionInstance.context() == WasmContext.get(null);
            CallTarget target = functionInstance.target();
            Object result = callNode.execute(target, validateArguments(arguments, WasmArguments.RUNTIME_ARGUMENT_COUNT));

            int resultCount = functionType.resultTypes().length;
            if (resultCount > 1) {
                return multiValueStackAsArray(getLanguage(WasmLanguage.class));
            } else {
                return result;
            }
        } catch (UnsupportedTypeException | ArityException e) {
            errorBranch.enter();
            throw WasmLanguage.rethrow(e);
        }
    }

    private Object[] validateArguments(Object[] arguments, int offset) throws ArityException, UnsupportedTypeException {
        final byte[] paramTypes = functionType.paramTypes();
        final int paramCount = paramTypes.length;
        CompilerAsserts.partialEvaluationConstant(paramCount);
        if (arguments.length - offset != paramCount) {
            throw ArityException.create(paramCount, paramCount, arguments.length - offset);
        }
        if (CompilerDirectives.inCompiledCode() && paramCount <= MAX_UNROLL) {
            validateArgumentsUnroll(arguments, offset, paramTypes, paramCount);
        } else {
            for (int i = 0; i < paramCount; i++) {
                validateArgument(arguments, offset, paramTypes, i);
            }
        }
        return arguments;
    }

    @ExplodeLoop
    private static void validateArgumentsUnroll(Object[] arguments, int offset, byte[] paramTypes, int paramCount) throws UnsupportedTypeException {
        for (int i = 0; i < paramCount; i++) {
            validateArgument(arguments, offset, paramTypes, i);
        }
    }

    private static void validateArgument(Object[] arguments, int offset, byte[] paramTypes, int i) throws UnsupportedTypeException {
        byte paramType = paramTypes[i];
        Object value = arguments[i + offset];
        switch (paramType) {
            case WasmType.I32_TYPE -> {
                if (value instanceof Integer) {
                    return;
                }
            }
            case WasmType.I64_TYPE -> {
                if (value instanceof Long) {
                    return;
                }
            }
            case WasmType.F32_TYPE -> {
                if (value instanceof Float) {
                    return;
                }
            }
            case WasmType.F64_TYPE -> {
                if (value instanceof Double) {
                    return;
                }
            }
            case WasmType.V128_TYPE -> {
                if (value instanceof Vector128) {
                    return;
                }
            }
            case WasmType.FUNCREF_TYPE -> {
                if (value instanceof WasmFunctionInstance || value == WasmConstant.NULL) {
                    return;
                }
            }
            case WasmType.EXTERNREF_TYPE -> {
                return;
            }
            default -> throw WasmException.create(Failure.UNKNOWN_TYPE);
        }
        throw UnsupportedTypeException.create(arguments);
    }

    private Object multiValueStackAsArray(WasmLanguage language) {
        final var multiValueStack = language.multiValueStack();
        final long[] primitiveMultiValueStack = multiValueStack.primitiveStack();
        final Object[] objectMultiValueStack = multiValueStack.objectStack();
        final byte[] resultTypes = functionType.resultTypes();
        final int resultCount = resultTypes.length;
        assert primitiveMultiValueStack.length >= resultCount;
        assert objectMultiValueStack.length >= resultCount;
        final Object[] values = new Object[resultCount];
        CompilerAsserts.partialEvaluationConstant(resultCount);
        if (CompilerDirectives.inCompiledCode() && resultCount <= MAX_UNROLL) {
            popMultiValueResultUnroll(values, primitiveMultiValueStack, objectMultiValueStack, resultTypes, resultCount);
        } else {
            for (int i = 0; i < resultCount; i++) {
                values[i] = popMultiValueResult(primitiveMultiValueStack, objectMultiValueStack, resultTypes, i);
            }
        }
        return InteropArray.create(values);
    }

    @ExplodeLoop
    private static void popMultiValueResultUnroll(Object[] values, long[] primitiveMultiValueStack, Object[] objectMultiValueStack, byte[] resultTypes, int resultCount) {
        for (int i = 0; i < resultCount; i++) {
            values[i] = popMultiValueResult(primitiveMultiValueStack, objectMultiValueStack, resultTypes, i);
        }
    }

    private static Object popMultiValueResult(long[] primitiveMultiValueStack, Object[] objectMultiValueStack, byte[] resultTypes, int i) {
        final byte resultType = resultTypes[i];
        return switch (resultType) {
            case WasmType.I32_TYPE -> (int) primitiveMultiValueStack[i];
            case WasmType.I64_TYPE -> primitiveMultiValueStack[i];
            case WasmType.F32_TYPE -> Float.intBitsToFloat((int) primitiveMultiValueStack[i]);
            case WasmType.F64_TYPE -> Double.longBitsToDouble(primitiveMultiValueStack[i]);
            case WasmType.V128_TYPE, WasmType.FUNCREF_TYPE, WasmType.EXTERNREF_TYPE -> {
                Object obj = objectMultiValueStack[i];
                objectMultiValueStack[i] = null;
                yield obj;
            }
            default -> throw WasmException.create(Failure.UNSPECIFIED_INTERNAL);
        };
    }

    // TODO: Do we need the 3 overrides below?
    @Override
    public String getName() {
        return "wasm-function-interop:" + functionType;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    protected boolean isInstrumentable() {
        return false;
    }
}
