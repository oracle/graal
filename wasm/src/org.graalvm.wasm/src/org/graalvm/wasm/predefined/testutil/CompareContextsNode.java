/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.predefined.testutil;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.wasm.GlobalRegistry;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmVoidResult;
import org.graalvm.wasm.exception.WasmExecutionException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;
import org.graalvm.wasm.predefined.testutil.SaveContextNode.ContextState;

/**
 * Records the context state (memory and global variables) into a custom object.
 */
public class CompareContextsNode extends WasmBuiltinRootNode {
    public CompareContextsNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public Object executeWithContext(VirtualFrame frame, WasmContext context) {
        final ContextState firstState = (ContextState) frame.getArguments()[0];
        final ContextState lastState = (ContextState) frame.getArguments()[0];
        compareContexts(firstState, lastState);
        return WasmVoidResult.getInstance();
    }

    @Override
    public String builtinNodeName() {
        return TestutilModule.Names.RESET_CONTEXT;
    }

    @CompilerDirectives.TruffleBoundary
    private void compareContexts(ContextState firstState, ContextState lastState) {
        compareMemories(firstState, lastState);
        compareGlobals(firstState, lastState);
    }

    private void compareGlobals(ContextState firstState, ContextState lastState) {
        final GlobalRegistry firstGlobals = firstState.globals();
        final GlobalRegistry lastGlobals = lastState.globals();
        if (firstGlobals.count() != lastGlobals.count()) {
            throw new WasmExecutionException(this, "Mismatch in memory lengths.");
        }
        for (int address = 0; address < firstGlobals.count(); address++) {
            long first = firstGlobals.loadAsLong(address);
            long last = lastGlobals.loadAsLong(address);
            if (first != last) {
                throw new WasmExecutionException(this, "Mismatch in global at " + address + ". " +
                                "Reference " + first + ", actual " + last);
            }
        }
    }

    private void compareMemories(ContextState firstState, ContextState lastState) {
        final WasmMemory firstMemory = firstState.memory();
        final WasmMemory lastMemory = lastState.memory();
        if (firstMemory == null && lastMemory == null) {
            return;
        }
        if (firstMemory == null || lastMemory == null) {
            throw new WasmExecutionException(this, "One of the memories is null.");
        }
        if (firstMemory.byteSize() != lastMemory.byteSize()) {
            throw new WasmExecutionException(this, "Mismatch in memory lengths: " + firstMemory.byteSize() + " vs " +
                            lastMemory.byteSize());
        }
        for (int ptr = 0; ptr < firstMemory.byteSize(); ptr++) {
            byte first = (byte) firstMemory.load_i32_8s(this, ptr);
            byte last = (byte) lastMemory.load_i32_8s(this, ptr);
            if (first != last) {
                long from = (ptr - 100) / 8 * 8;
                throw new WasmExecutionException(this, "Memory mismatch.\n" +
                                "-- Reference --\n" + firstMemory.hexView(from, 200) + "\n" +
                                "-- Actual --\n" + firstMemory.hexView(from, 200) + "\n");
            }
        }
    }
}
