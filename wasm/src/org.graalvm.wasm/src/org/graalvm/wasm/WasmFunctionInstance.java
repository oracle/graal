/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;

@ExportLibrary(InteropLibrary.class)
public final class WasmFunctionInstance extends EmbedderDataHolder implements TruffleObject {

    private final WasmContext context;
    private final WasmInstance moduleInstance;
    private final WasmFunction function;
    private final CallTarget target;
    /**
     * Stores the imported function object for {@link org.graalvm.wasm.api.ExecuteHostFunctionNode}.
     * Initialized during linking.
     */
    private Object importedFunction;

    /**
     * Represents a call target that is a WebAssembly function or an imported function.
     */
    public WasmFunctionInstance(WasmInstance moduleInstance, WasmFunction function, CallTarget target) {
        this(moduleInstance.context(), moduleInstance, function, target);
    }

    public WasmFunctionInstance(WasmContext context, WasmInstance moduleInstance, WasmFunction function, CallTarget target) {
        this.context = Objects.requireNonNull(context, "context must be non-null");
        this.moduleInstance = Objects.requireNonNull(moduleInstance, "module instance must be non-null");
        this.function = Objects.requireNonNull(function, "function must be non-null");
        this.target = Objects.requireNonNull(target, "Call target must be non-null");
        assert ((RootCallTarget) target).getRootNode().getLanguage(WasmLanguage.class) == context.language();
    }

    @Override
    public String toString() {
        return name();
    }

    public WasmStore store() {
        return moduleInstance.store();
    }

    public WasmContext context() {
        return context;
    }

    public TruffleContext getTruffleContext() {
        return context.environment().getContext();
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
    static class Execute {
        private static Object execute(WasmFunctionInstance functionInstance, Object[] arguments, CallTarget callAdapter, Node callNode) {
            return callAdapter.call(callNode, WasmArguments.create(functionInstance, arguments));
            // throws ArityException, UnsupportedTypeException
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"actualFunction == cachedFunction"}, limit = "2")
        static Object direct(WasmFunctionInstance functionInstance, Object[] arguments,
                        @Bind("functionInstance.function()") WasmFunction actualFunction,
                        @Cached("actualFunction") WasmFunction cachedFunction,
                        @Cached("getOrCreateInteropCallAdapter(functionInstance)") CallTarget cachedCallAdapter,
                        @Bind Node node) {
            return execute(functionInstance, arguments, cachedCallAdapter, node);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"actualCallAdapter == cachedCallAdapter"}, limit = "3", replaces = "direct")
        static Object directAdapter(WasmFunctionInstance functionInstance, Object[] arguments,
                        @Bind("getOrCreateInteropCallAdapter(functionInstance)") CallTarget actualCallAdapter,
                        @Cached("actualCallAdapter") CallTarget cachedCallAdapter,
                        @Bind Node node) {
            return execute(functionInstance, arguments, cachedCallAdapter, node);
        }

        @Specialization(replaces = "directAdapter")
        static Object indirect(WasmFunctionInstance functionInstance, Object[] arguments,
                        @Bind Node node) {
            CallTarget callAdapter = getOrCreateInteropCallAdapter(functionInstance);
            Node callNode = node.isAdoptable() ? node : EncapsulatingNodeReference.getCurrent().get();
            return execute(functionInstance, arguments, callAdapter, callNode);
        }

        static CallTarget getOrCreateInteropCallAdapter(WasmFunctionInstance functionInstance) {
            WasmFunction function = functionInstance.function();
            CallTarget callAdapter = function.getInteropCallAdapter();
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, callAdapter == null)) {
                return function.getOrCreateInteropCallAdapter(functionInstance.context().language());
            }
            return callAdapter;
        }
    }
}
