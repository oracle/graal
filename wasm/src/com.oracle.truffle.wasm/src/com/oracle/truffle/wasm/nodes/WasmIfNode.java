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
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.wasm.WasmCodeEntry;
import com.oracle.truffle.wasm.WasmContext;
import com.oracle.truffle.wasm.WasmModule;
import com.oracle.truffle.wasm.constants.TargetOffset;

public final class WasmIfNode extends WasmNode {

    @CompilationFinal private final byte returnTypeId;
    @CompilationFinal private final int initialStackPointer;
    @Child private WasmNode trueBranch;
    @Child private WasmNode falseBranch;

    private final ConditionProfile condition = ConditionProfile.createCountingProfile();

    public WasmIfNode(WasmModule wasmModule, WasmCodeEntry codeEntry, WasmNode trueBranch, WasmNode falseBranch, int byteLength, byte returnTypeId, int initialStackPointer, int byteConstantLength,
                    int numericLiteralLength) {
        super(wasmModule, codeEntry, byteLength, byteConstantLength, numericLiteralLength);
        this.returnTypeId = returnTypeId;
        this.initialStackPointer = initialStackPointer;
        this.trueBranch = trueBranch;
        this.falseBranch = falseBranch;
    }

    @Override
    public TargetOffset execute(WasmContext context, VirtualFrame frame) {
        int stackPointer = initialStackPointer - 1;
        if (condition.profile(popInt(frame, stackPointer) != 0)) {
            trace("taking if branch");
            return trueBranch.execute(context, frame);
        } else {
            trace("taking else branch");
            return falseBranch.execute(context, frame);
        }
    }

    @Override
    public byte returnTypeId() {
        return returnTypeId;
    }

    @Override
    public int intConstantLength() {
        return trueBranch.intConstantLength() + falseBranch.intConstantLength();
    }

    @Override
    public int branchTableLength() {
        return trueBranch.branchTableLength() + falseBranch.branchTableLength();
    }
}
