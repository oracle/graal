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
import org.graalvm.wasm.nodes.WasmIndirectCallNode;

import com.oracle.truffle.api.CallTarget;
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
    private final WasmFunction function;
    private final CallTarget target;
    private final TruffleContext truffleContext;

    /**
     * Represents a call target that is a WebAssembly function or an imported function.
     *
     * If the function is imported, then context UID and the function are set to {@code null}.
     */
    public WasmFunctionInstance(WasmContext context, WasmFunction function, CallTarget target) {
        Assert.assertNotNull(target, "Call target must be non-null", Failure.UNSPECIFIED_INTERNAL);
        this.context = context;
        this.function = function;
        this.target = target;
        this.truffleContext = context.environment().getContext();
    }

    @Override
    public String toString() {
        return name();
    }

    public WasmContext context() {
        return context;
    }

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
            return callNode.execute(target, arguments);
        } finally {
            c.leave(self, prev);
        }
    }
}
