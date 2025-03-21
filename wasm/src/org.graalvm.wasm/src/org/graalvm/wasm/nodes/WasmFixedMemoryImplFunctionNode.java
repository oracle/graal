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
package org.graalvm.wasm.nodes;

import java.util.Arrays;

import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.memory.WasmMemoryLibrary;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Dispatches to specialized instances of {@link WasmInstrumentableFunctionNode}, ensuring that each
 * {@link WasmInstrumentableFunctionNode} sees only one implementation of {@link WasmMemoryLibrary}
 * for each memory, meaning that all memory accesses are monomorphic in the compiled
 * {@link WasmFunctionNode}. If a cache limit is exceeded (too many combinations of memory
 * implementations), a {@link WasmInstrumentableFunctionNode} using dispatched libraries for each
 * memory will be used instead.
 */
public abstract class WasmFixedMemoryImplFunctionNode extends Node {

    private final WasmModule module;
    private final WasmCodeEntry codeEntry;
    private final int bytecodeStartOffset;
    private final int bytecodeEndOffset;
    private final Node[] callNodes;

    private static final WasmFunctionBaseNode[] EMPTY_FUNCTION_BASE_NODES = new WasmFunctionBaseNode[0];

    @Children private WasmFunctionBaseNode[] functionBaseNodes = EMPTY_FUNCTION_BASE_NODES;

    protected WasmFixedMemoryImplFunctionNode(WasmModule module, WasmCodeEntry codeEntry, int bytecodeStartOffset, int bytecodeEndOffset, Node[] callNodes) {
        this.module = module;
        this.codeEntry = codeEntry;
        this.bytecodeStartOffset = bytecodeStartOffset;
        this.bytecodeEndOffset = bytecodeEndOffset;
        this.callNodes = callNodes;
    }

    public static WasmFixedMemoryImplFunctionNode create(WasmModule module, WasmCodeEntry codeEntry, int bytecodeStartOffset, int bytecodeEndOffset, Node[] callNodes) {
        return WasmFixedMemoryImplFunctionNodeGen.create(module, codeEntry, bytecodeStartOffset, bytecodeEndOffset, callNodes);
    }

    @Specialization(guards = {"memoryCount() == 1"}, limit = "3")
    protected void doFixedMemoryImpl(VirtualFrame frame, WasmInstance instance,
                    @CachedLibrary(value = "instance.memory(0)") @SuppressWarnings("unused") WasmMemoryLibrary cachedMemoryLib0,
                    @Cached("createMemoryLibs1(cachedMemoryLib0)") @SuppressWarnings("unused") WasmMemoryLibrary[] cachedMemoryLibs,
                    @Cached(value = "createSpecializedFunctionNode(cachedMemoryLibs)", adopt = false) WasmFunctionBaseNode specializedFunctionNode) {
        specializedFunctionNode.execute(frame, instance);
    }

    @Specialization(replaces = "doFixedMemoryImpl")
    protected void doDispatched(VirtualFrame frame, WasmInstance instance,
                    @Cached(value = "createDispatchedFunctionNode()", adopt = false) WasmFunctionBaseNode dispatchedFunctionNode) {
        dispatchedFunctionNode.execute(frame, instance);
    }

    @NeverDefault
    protected WasmMemoryLibrary[] createMemoryLibs1(WasmMemoryLibrary memoryLibs) {
        return new WasmMemoryLibrary[]{memoryLibs};
    }

    @Idempotent
    protected int memoryCount() {
        return module.memoryCount();
    }

    @NeverDefault
    protected WasmFunctionBaseNode createSpecializedFunctionNode(WasmMemoryLibrary[] memoryLibs) {
        CompilerAsserts.neverPartOfCompilation();
        WasmInstrumentableFunctionNode instrumentableFunctionNode = new WasmInstrumentableFunctionNode(module, codeEntry, bytecodeStartOffset, bytecodeEndOffset, callNodes, memoryLibs);
        WasmFunctionBaseNode baseNode = new WasmFunctionBaseNode(instrumentableFunctionNode);
        functionBaseNodes = Arrays.copyOf(functionBaseNodes, functionBaseNodes.length + 1);
        functionBaseNodes[functionBaseNodes.length - 1] = insert(baseNode);
        notifyInserted(instrumentableFunctionNode);
        return baseNode;
    }

    @NeverDefault
    protected WasmFunctionBaseNode createDispatchedFunctionNode() {
        CompilerAsserts.neverPartOfCompilation();
        WasmMemoryLibrary[] memoryLibs = new WasmMemoryLibrary[module.memoryCount()];
        for (int memoryIndex = 0; memoryIndex < module.memoryCount(); memoryIndex++) {
            memoryLibs[memoryIndex] = insert(WasmMemoryLibrary.getFactory().createDispatched(3));
        }
        WasmInstrumentableFunctionNode instrumentableFunctionNode = new WasmInstrumentableFunctionNode(module, codeEntry, bytecodeStartOffset, bytecodeEndOffset, callNodes, memoryLibs);
        WasmFunctionBaseNode baseNode = new WasmFunctionBaseNode(instrumentableFunctionNode);
        functionBaseNodes = new WasmFunctionBaseNode[]{insert(baseNode)};
        notifyInserted(instrumentableFunctionNode);
        return baseNode;
    }

    public abstract void execute(VirtualFrame frame, WasmInstance instance);

    final Node[] getCallNodes() {
        return callNodes;
    }
}
