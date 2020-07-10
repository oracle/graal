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
package org.graalvm.wasm.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmModule;

public abstract class WasmNode extends Node implements WasmNodeInterface {
    // TODO: We should not cache the module in the nodes, only the symbol table.
    private final WasmModule wasmModule;
    private final WasmCodeEntry codeEntry;

    /**
     * The length (in bytes) of the control structure in the instructions stream, without the
     * initial opcode and the block return type.
     */
    @CompilationFinal private int byteLength;

    public WasmNode(WasmModule wasmModule, WasmCodeEntry codeEntry, int byteLength) {
        this.wasmModule = wasmModule;
        this.codeEntry = codeEntry;
        this.byteLength = byteLength;
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
    public abstract int execute(WasmContext context, VirtualFrame frame);

    public abstract byte returnTypeId();

    @SuppressWarnings("hiding")
    protected final void initialize(int byteLength) {
        this.byteLength = byteLength;
    }

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

    int byteLength() {
        return byteLength;
    }

    abstract int byteConstantLength();

    abstract int intConstantLength();

    abstract int longConstantLength();

    abstract int branchTableLength();

    abstract int profileCount();

}
