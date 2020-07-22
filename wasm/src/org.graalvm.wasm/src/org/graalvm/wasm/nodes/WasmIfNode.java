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
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmModule;

import static org.graalvm.wasm.WasmTracing.trace;

public final class WasmIfNode extends WasmNode {

    @CompilationFinal private final byte returnTypeId;
    @CompilationFinal private final int initialStackPointer;
    @Child private WasmNode trueBranch;
    @Child private WasmNode falseBranch;

    private final ConditionProfile condition = ConditionProfile.createCountingProfile();

    public WasmIfNode(WasmModule wasmModule, WasmCodeEntry codeEntry, WasmNode trueBranch, WasmNode falseBranch, int byteLength, byte returnTypeId, int initialStackPointer) {
        super(wasmModule, codeEntry, byteLength);
        this.returnTypeId = returnTypeId;
        this.initialStackPointer = initialStackPointer;
        this.trueBranch = trueBranch;
        this.falseBranch = falseBranch;
    }

    @Override
    public int execute(WasmContext context, VirtualFrame frame) {
        int stackPointer = initialStackPointer - 1;
        if (condition.profile(popInt(frame, stackPointer) != 0)) {
            trace("taking if branch");
            return trueBranch.execute(context, frame);
        } else if (falseBranch != null) {
            trace("taking else branch");
            return falseBranch.execute(context, frame);
        } else {
            return -1;
        }
    }

    @Override
    public byte returnTypeId() {
        return returnTypeId;
    }

    @Override
    int byteConstantLength() {
        return trueBranch.byteConstantLength() + (falseBranch != null ? falseBranch.byteConstantLength() : 0);
    }

    @Override
    int intConstantLength() {
        return trueBranch.intConstantLength() + (falseBranch != null ? falseBranch.intConstantLength() : 0);
    }

    @Override
    int longConstantLength() {
        return trueBranch.longConstantLength() + (falseBranch != null ? falseBranch.longConstantLength() : 0);
    }

    @Override
    int branchTableLength() {
        return trueBranch.branchTableLength() + (falseBranch != null ? falseBranch.branchTableLength() : 0);
    }

    @Override
    int profileCount() {
        return trueBranch.profileCount() + (falseBranch != null ? falseBranch.profileCount() : 0);
    }
}
