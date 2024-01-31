/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;

public class ExecuteInParentContextNode extends WasmBuiltinRootNode {
    private final Object executable;
    @CompilationFinal private int functionTypeIndex = -1;
    private final BranchProfile errorBranch = BranchProfile.create();
    @Child private InteropLibrary functionInterop;
    @Child private InteropLibrary arrayInterop;
    @Child private InteropLibrary resultInterop;

    public ExecuteInParentContextNode(WasmLanguage language, WasmModule module, Object executable, int resultCount) {
        super(language, module);
        this.executable = executable;
        this.functionInterop = InteropLibrary.getUncached(executable);
        if (resultCount > 1) {
            this.arrayInterop = InteropLibrary.getFactory().createDispatched(5);
        }
    }

    void setFunctionTypeIndex(int functionTypeIndex) {
        this.functionTypeIndex = functionTypeIndex;
    }

    @Override
    public Object executeWithContext(VirtualFrame frame, WasmContext context, WasmInstance instance) {
        // Imported executables come from the parent context
        TruffleContext truffleContext = context.environment().getContext().getParent();
        Object[] arguments = WasmArguments.getArguments(frame.getArguments());
        try {
            Object prev = truffleContext.enter(this);
            Object result;
            try {
                result = functionInterop.execute(executable, arguments);
            } finally {
                truffleContext.leave(this, prev);
            }
            int resultCount = module().symbolTable().functionTypeResultCount(functionTypeIndex);
            CompilerAsserts.partialEvaluationConstant(resultCount);
            if (resultCount == 0) {
                return WasmConstant.VOID;
            } else if (resultCount == 1) {
                byte resultType = module().symbolTable().functionTypeResultTypeAt(functionTypeIndex, 0);
                return convertResult(result, resultType);
            } else {
                pushMultiValueResult(result, resultCount);
                return WasmConstant.MULTI_VALUE;
            }
        } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
            errorBranch.enter();
            throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Call failed: %s", getMessage(e));
        }
    }

    /**
     * Convert result to a WebAssembly value. Note: In most cases, the result values will already be
     * of the correct boxed type because they're already converted on the JS side, so we only need
     * to unbox and and can forego InteropLibrary.
     */
    private Object convertResult(Object result, byte resultType) throws UnsupportedMessageException {
        CompilerAsserts.partialEvaluationConstant(resultType);
        return switch (resultType) {
            case WasmType.I32_TYPE -> asInt(result);
            case WasmType.I64_TYPE -> asLong(result);
            case WasmType.F32_TYPE -> asFloat(result);
            case WasmType.F64_TYPE -> asDouble(result);
            case WasmType.V128_TYPE, WasmType.FUNCREF_TYPE, WasmType.EXTERNREF_TYPE -> result;
            default -> {
                throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown result type: %d", resultType);
            }
        };
    }

    @ExplodeLoop
    private void pushMultiValueResult(Object result, int resultCount) {
        CompilerAsserts.partialEvaluationConstant(resultCount);
        if (!arrayInterop.hasArrayElements(result)) {
            errorBranch.enter();
            throw WasmException.create(Failure.UNSUPPORTED_MULTI_VALUE_TYPE);
        }
        try {
            final int size = (int) arrayInterop.getArraySize(result);
            if (size != resultCount) {
                errorBranch.enter();
                throw WasmException.create(Failure.INVALID_MULTI_VALUE_ARITY);
            }
            final var multiValueStack = WasmLanguage.get(this).multiValueStack();
            multiValueStack.resize(resultCount);
            final long[] primitiveMultiValueStack = multiValueStack.primitiveStack();
            final Object[] objectMultiValueStack = multiValueStack.objectStack();
            for (int i = 0; i < resultCount; i++) {
                byte resultType = module().symbolTable().functionTypeResultTypeAt(functionTypeIndex, i);
                CompilerAsserts.partialEvaluationConstant(resultType);
                Object value = arrayInterop.readArrayElement(result, i);
                switch (resultType) {
                    case WasmType.I32_TYPE -> primitiveMultiValueStack[i] = asInt(value);
                    case WasmType.I64_TYPE -> primitiveMultiValueStack[i] = asLong(value);
                    case WasmType.F32_TYPE -> primitiveMultiValueStack[i] = Float.floatToRawIntBits(asFloat(value));
                    case WasmType.F64_TYPE -> primitiveMultiValueStack[i] = Double.doubleToRawLongBits(asDouble(value));
                    case WasmType.V128_TYPE -> {
                        if (!(value instanceof Vector128)) {
                            errorBranch.enter();
                            throw WasmException.create(Failure.INVALID_TYPE_IN_MULTI_VALUE);
                        }
                        objectMultiValueStack[i] = value;
                    }
                    case WasmType.FUNCREF_TYPE, WasmType.EXTERNREF_TYPE -> objectMultiValueStack[i] = value;
                    default -> {
                        errorBranch.enter();
                        throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown result type: %d", resultType);
                    }
                }
            }
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            errorBranch.enter();
            throw WasmException.create(Failure.INVALID_TYPE_IN_MULTI_VALUE);
        }
    }

    private int asInt(Object result) throws UnsupportedMessageException {
        if (result instanceof Integer i) {
            return i;
        } else {
            return resultInterop().asInt(result);
        }
    }

    private long asLong(Object result) throws UnsupportedMessageException {
        if (result instanceof Long l) {
            return l;
        } else {
            return resultInterop().asLong(result);
        }
    }

    private float asFloat(Object result) throws UnsupportedMessageException {
        if (result instanceof Float f) {
            return f;
        } else {
            return resultInterop().asFloat(result);
        }
    }

    private double asDouble(Object result) throws UnsupportedMessageException {
        if (result instanceof Double d) {
            return d;
        } else {
            return resultInterop().asDouble(result);
        }
    }

    private InteropLibrary resultInterop() {
        InteropLibrary interop = resultInterop;
        if (interop == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            interop = insert(InteropLibrary.getFactory().createDispatched(5));
        }
        return interop;
    }

    @TruffleBoundary
    private static String getMessage(InteropException e) {
        return e.getMessage();
    }

    @Override
    public String builtinNodeName() {
        return "execute";
    }
}
