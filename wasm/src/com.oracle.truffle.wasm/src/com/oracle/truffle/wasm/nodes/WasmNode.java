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

package com.oracle.truffle.wasm.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.wasm.WasmCodeEntry;
import com.oracle.truffle.wasm.WasmContext;
import com.oracle.truffle.wasm.WasmModule;
import com.oracle.truffle.wasm.constants.TargetOffset;

public abstract class WasmNode extends Node implements WasmNodeInterface {
    // TODO: We should not cache the module in the nodes, only the symbol table.
    @CompilationFinal private final WasmModule wasmModule;
    @CompilationFinal private final WasmCodeEntry codeEntry;

    /**
     * The length (in bytes) of the control structure in the instructions stream, without the
     * initial opcode and the block return type.
     */
    @CompilationFinal private int byteLength;

    /**
     * The number of bytes in the byte constant table used by this node.
     */
    @CompilationFinal private int byteConstantLength;

    /**
     * The number of integers in the int constant table used by this node.
     */
    @CompilationFinal private int intConstantLength;

    /**
     * The number of literals in the numeric literals table used by this node.
     */
    @CompilationFinal private int longConstantLength;

    /**
     * The number of branch tables used by this node.
     */
    @CompilationFinal private int branchTableLength;

    public WasmNode(WasmModule wasmModule, WasmCodeEntry codeEntry, int byteLength, int byteConstantLength, int longConstantLength) {
        this.wasmModule = wasmModule;
        this.codeEntry = codeEntry;
        this.byteLength = byteLength;
        this.byteConstantLength = byteConstantLength;
        this.longConstantLength = longConstantLength;
    }

    /**
     * Execute the current node within the given frame and return the branch target.
     *
     * @param frame The frame to use for execution.
     * @return The return value of this method indicates whether a branch is to be executed, in case
     *         of nested blocks. An offset with value -1 means no branch, whereas a return value n
     *         greater than or equal to 0 means that the execution engine has to branch n levels up
     *         the block execution stack.
     */
    public abstract TargetOffset execute(WasmContext context, VirtualFrame frame);

    public abstract byte returnTypeId();

    protected static final int typeLength(int typeId) {
        switch (typeId) {
            case 0x00:
            case 0x40:
                return 0;
            default:
                return 1;
        }
    }

    int returnTypeLength() {
        return typeLength(returnTypeId());
    }

    @Override
    public final WasmCodeEntry codeEntry() {
        return codeEntry;
    }

    public final WasmModule module() {
        return wasmModule;
    }

    final void initialize(int byteLength, int byteConstantLength, int intConstantLength, int longConstantLength, int branchTableLength) {
        this.byteLength = byteLength;
        this.byteConstantLength = byteConstantLength;
        this.intConstantLength = intConstantLength;
        this.longConstantLength = longConstantLength;
        this.branchTableLength = branchTableLength;
    }

    int byteLength() {
        return byteLength;
    }

    int byteConstantLength() {
        return byteConstantLength;
    }

    int intConstantLength() {
        return intConstantLength;
    }

    int longConstantLength() {
        return longConstantLength;
    }

    int branchTableLength() {
        return branchTableLength;
    }
}
