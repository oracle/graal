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
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.wasm.binary.memory.WasmMemory;

public class Memories {
    private static final int INITIAL_MEMORIES_SIZE = 4;

    @CompilationFinal(dimensions = 1) private WasmMemory[] memories;
    private int numMemories;

    public Memories() {
        this.memories = new WasmMemory[INITIAL_MEMORIES_SIZE];
        this.numMemories = 0;
    }

    private void ensureCapacity() {
        if (numMemories == memories.length) {
            final WasmMemory[] updatedMemories = new WasmMemory[memories.length * 2];
            System.arraycopy(memories, 0, updatedMemories, 0, memories.length);
            memories = updatedMemories;
        }
    }

    public int memoryCount() {
        return numMemories;
    }

    public int allocateMemory(WasmMemory memory) {
        ensureCapacity();
        memories[numMemories] = memory;
        int idx = numMemories;
        numMemories++;
        return idx;
    }

    public WasmMemory memory(int index) {
        assert index < numMemories;
        return memories[index];
    }
}
