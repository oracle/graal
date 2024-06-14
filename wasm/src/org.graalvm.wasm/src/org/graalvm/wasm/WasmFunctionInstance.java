/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.api.InteropArray;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.nodes.WasmIndirectCallNode;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public final class WasmFunctionInstance extends EmbedderDataHolder implements TruffleObject {
    private final WasmContext context;
    private final WasmInstance moduleInstance;
    private final WasmFunction function;
    private final CallTarget target;
    private final TruffleContext truffleContext;
    private Object importedFunction;

    /**
     * Represents a call target that is a WebAssembly function or an imported function.
     * <p>
     * If the function is imported, then context UID and the function are set to {@code null}.
     */
    public WasmFunctionInstance(WasmInstance moduleInstance, WasmFunction function, CallTarget target) {
        this(moduleInstance.context(), moduleInstance, function, target);
    }

    /**
     * Constructor for custom (test) functions not defined in a wasm module.
     */
    public WasmFunctionInstance(WasmContext context, CallTarget target) {
        this(context, null, null, target);
    }

    public WasmFunctionInstance(WasmContext context, WasmInstance moduleInstance, WasmFunction function, CallTarget target) {
        Assert.assertNotNull(target, "Call target must be non-null", Failure.UNSPECIFIED_INTERNAL);
        this.context = context;
        this.moduleInstance = moduleInstance;
        this.function = function;
        this.target = target;
        this.truffleContext = context.environment().getContext();
        assert ((RootCallTarget) target).getRootNode().getLanguage(WasmLanguage.class) == context.language();
    }

    @Override
    public String toString() {
        return name();
    }

    public WasmContext context() {
        return context;
    }

    public WasmInstance moduleInstance() {
        return moduleInstance;
    }

    @TruffleBoundary
    public String name() {
        if (function == null) {
            return target.toString();
        }
        return function.name();
    }

    public WasmFunction function() {
        return function;
    }

    public CallTarget target() {
        return target;
    }

    public TruffleContext getTruffleContext() {
        return truffleContext;
    }

    public void setImportedFunction(Object importedFunction) {
        this.importedFunction = importedFunction;
    }

    public Object getImportedFunction() {
        return importedFunction;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] arguments,
                    @CachedLibrary("this") InteropLibrary self,
                    @Cached WasmIndirectCallNode callNode) {
        TruffleContext c = getTruffleContext();
        Object prev = c.enter(self);
        try {
            Object result = callNode.execute(target, WasmArguments.create(moduleInstance, arguments));

            // For external calls of a WebAssembly function we have to materialize the multi-value
            // stack.
            // At this point the multi-value stack has already been populated, therefore, we don't
            // have to check the size of the multi-value stack.
            if (result == WasmConstant.MULTI_VALUE) {
                WasmLanguage language = context.language();
                assert language == WasmLanguage.get(null);
                return multiValueStackAsArray(language);
            }
            return result;
        } finally {
            c.leave(self, prev);
        }
    }

    private Object multiValueStackAsArray(WasmLanguage language) {
        final var multiValueStack = language.multiValueStack();
        final long[] primitiveMultiValueStack = multiValueStack.primitiveStack();
        final Object[] objectMultiValueStack = multiValueStack.objectStack();
        final int resultCount = function.resultCount();
        assert primitiveMultiValueStack.length >= resultCount;
        assert objectMultiValueStack.length >= resultCount;
        final Object[] values = new Object[resultCount];
        for (int i = 0; i < resultCount; i++) {
            byte resultType = function.resultTypeAt(i);
            values[i] = switch (resultType) {
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
        return InteropArray.create(values);
    }
}
