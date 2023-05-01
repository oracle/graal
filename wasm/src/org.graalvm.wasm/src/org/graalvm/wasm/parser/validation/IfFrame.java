/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.parser.validation;

import java.util.Arrays;

import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.bytecode.RuntimeBytecodeGen;

/**
 * Representation of a wasm if and else block during module validation.
 */
class IfFrame extends ControlFrame {

    private final IntArrayList branchTargets;
    private int falseJumpLocation;
    private boolean elseBranch;

    IfFrame(byte[] paramTypes, byte[] resultTypes, int initialStackSize, boolean unreachable, int falseJumpLocation) {
        super(paramTypes, resultTypes, initialStackSize, unreachable);
        branchTargets = new IntArrayList();
        this.falseJumpLocation = falseJumpLocation;
        this.elseBranch = false;
    }

    @Override
    byte[] labelTypes() {
        return resultTypes();
    }

    @Override
    void enterElse(ParserState state, RuntimeBytecodeGen bytecode) {
        final int location = bytecode.addBranchLocation();
        bytecode.patchLocation(falseJumpLocation, bytecode.location());
        falseJumpLocation = location;
        elseBranch = true;
        state.checkStackAfterFrameExit(this, resultTypes());
        // Since else is a separate block the unreachable state has to be reset.
        resetUnreachable();
    }

    @Override
    void exit(RuntimeBytecodeGen bytecode) {
        if (!elseBranch && !Arrays.equals(paramTypes(), resultTypes())) {
            throw WasmException.create(Failure.TYPE_MISMATCH, "Expected else branch. If with incompatible param and result types requires else branch.");
        }
        if (branchTargets.size() == 0) {
            bytecode.patchLocation(falseJumpLocation, bytecode.location());
        } else {
            final int location = bytecode.addLabel(resultTypeLength(), initialStackSize(), commonResultType());
            bytecode.patchLocation(falseJumpLocation, location);
            for (int branchLocation : branchTargets.toArray()) {
                bytecode.patchLocation(branchLocation, location);
            }
        }
    }

    @Override
    void addBranch(RuntimeBytecodeGen bytecode) {
        branchTargets.add(bytecode.addBranchLocation());
    }

    @Override
    void addBranchIf(RuntimeBytecodeGen bytecode) {
        branchTargets.add(bytecode.addBranchIfLocation());
    }

    @Override
    void addBranchTableItem(RuntimeBytecodeGen bytecode) {
        branchTargets.add(bytecode.addBranchTableItemLocation());
    }
}
