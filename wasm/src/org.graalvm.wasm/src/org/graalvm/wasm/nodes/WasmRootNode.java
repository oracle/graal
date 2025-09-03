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
package org.graalvm.wasm.nodes;

import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;

@NodeInfo(language = WasmLanguage.ID, description = "The root node of all WebAssembly functions")
public abstract class WasmRootNode extends RootNode {

    private final WasmModule module;

    private final BranchProfile nonLinkedProfile = BranchProfile.create();
    /** Bound module instance (single-context mode only). */
    @CompilationFinal private WasmInstance boundInstance;

    public WasmRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, WasmModule module) {
        super(language, frameDescriptor);
        this.module = module;
    }

    protected final WasmContext getContext() {
        return WasmContext.get(this);
    }

    protected WasmModule module() {
        return module;
    }

    @SuppressWarnings("static-method")
    public final void tryInitialize(WasmInstance instance) {
        // We want to ensure that linking always precedes the running of the WebAssembly code.
        // This linking should be as late as possible, because a WebAssembly context should
        // be able to parse multiple modules before the code gets run.
        if (!instance.isLinkCompletedFastPath()) {
            nonLinkedProfile.enter();
            instance.store().linker().tryLinkFastPath(instance);
        }
    }

    @Override
    protected int findBytecodeIndex(Node node, Frame frame) {
        if (node == null) {
            // uncached wasm calls without location may happen
            return -1;
        }
        if (node instanceof WasmCallNode n) {
            // cached wasm call with location may happen
            return n.getBytecodeOffset();
        } else {
            // for wasm exceptions we might be able to get the stack trace
            return -1;
        }
    }

    protected final WasmInstance instance(VirtualFrame frame) {
        WasmInstance instance = boundInstance;
        if (instance == null) {
            instance = WasmArguments.getModuleInstance(frame.getArguments());
        } else {
            CompilerAsserts.partialEvaluationConstant(instance);
            assert instance == WasmArguments.getModuleInstance(frame.getArguments());
        }
        assert instance == instance.store().lookupModuleInstance(module());
        return instance;
    }

    public final void setBoundModuleInstance(WasmInstance boundInstance) {
        CompilerAsserts.neverPartOfCompilation();
        assert this.boundInstance == null;
        this.boundInstance = boundInstance;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        assert WasmArguments.isValid(frame.getArguments());
        final WasmInstance instance = instance(frame);
        tryInitialize(instance);
        return executeWithInstance(frame, instance);
    }

    public abstract Object executeWithInstance(VirtualFrame frame, WasmInstance instance);

    @Override
    public final String toString() {
        return getName();
    }

    @Override
    protected boolean isInstrumentable() {
        return false;
    }

    @Override
    public boolean isInternal() {
        return true;
    }
}
