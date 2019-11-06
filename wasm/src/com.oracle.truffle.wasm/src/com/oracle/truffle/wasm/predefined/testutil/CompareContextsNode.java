/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.predefined.testutil;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.wasm.Globals;
import com.oracle.truffle.wasm.WasmCodeEntry;
import com.oracle.truffle.wasm.WasmLanguage;
import com.oracle.truffle.wasm.WasmVoidResult;
import com.oracle.truffle.wasm.exception.WasmExecutionException;
import com.oracle.truffle.wasm.memory.WasmMemory;
import com.oracle.truffle.wasm.predefined.WasmPredefinedRootNode;
import com.oracle.truffle.wasm.predefined.testutil.SaveContextNode.ContextState;

/**
 * Records the context state (memory and global variables) into a custom object.
 */
public class CompareContextsNode extends WasmPredefinedRootNode {
    public CompareContextsNode(WasmLanguage language, WasmCodeEntry codeEntry, WasmMemory memory) {
        super(language, codeEntry, memory);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final ContextState firstState = (ContextState) frame.getArguments()[0];
        final ContextState lastState = (ContextState) frame.getArguments()[0];
        compareContexts(firstState, lastState);
        return WasmVoidResult.getInstance();
    }

    @Override
    public String name() {
        return TestutilModule.Names.RESET_CONTEXT;
    }

    @CompilerDirectives.TruffleBoundary
    private void compareContexts(ContextState firstState, ContextState lastState) {
        compareMemories(firstState, lastState);
        compareGlobals(firstState, lastState);
    }

    private void compareGlobals(ContextState firstState, ContextState lastState) {
        final Globals firstGlobals = firstState.globals();
        final Globals lastGlobals = lastState.globals();
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
        if (firstMemory.byteSize() != lastMemory.byteSize()) {
            throw new WasmExecutionException(this, "Mismatch in memory lengths: " + firstMemory.byteSize() + " vs " +
                            lastMemory.byteSize());
        }
        for (int ptr = 0; ptr < firstMemory.byteSize(); ptr++) {
            byte first = (byte) firstMemory.load_i32_8s(ptr);
            byte last = (byte) lastMemory.load_i32_8s(ptr);
            if (first != last) {
                long from = (ptr - 100) / 8 * 8;
                throw new WasmExecutionException(this, "Memory mismatch.\n" +
                                "-- Reference --\n" + firstMemory.hexView(from, 200) + "\n" +
                                "-- Actual --\n" + firstMemory.hexView(from, 200) + "\n");
            }
        }
    }
}
