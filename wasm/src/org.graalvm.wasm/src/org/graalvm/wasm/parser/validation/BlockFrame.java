/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.BitSet;

import org.graalvm.wasm.SymbolTable;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.parser.bytecode.BytecodeFixup;
import org.graalvm.wasm.parser.bytecode.RuntimeBytecodeGen;

/**
 * Representation of a wasm block during module validation.
 */
class BlockFrame extends ControlFrame {
    private final ArrayList<BytecodeFixup> labelFixups;

    private BlockFrame(int[] paramTypes, int[] resultTypes, SymbolTable symbolTable, int initialStackSize, BitSet initializedLocals, int legacyCatchDepth) {
        super(paramTypes, resultTypes, symbolTable, initialStackSize, initializedLocals, legacyCatchDepth);
        labelFixups = new ArrayList<>();
    }

    BlockFrame(int[] paramTypes, int[] resultTypes, int initialStackSize, ControlFrame parentFrame) {
        this(paramTypes, resultTypes, parentFrame.getSymbolTable(), initialStackSize, (BitSet) parentFrame.initializedLocals.clone(), parentFrame.legacyCatchDepth());
    }

    static BlockFrame createFunctionFrame(int[] paramTypes, int[] resultTypes, int[] locals, SymbolTable symbolTable) {
        BitSet initializedLocals = new BitSet(locals.length);
        for (int localIndex = 0; localIndex < locals.length; localIndex++) {
            if (localIndex < paramTypes.length || WasmType.hasDefaultValue(locals[localIndex])) {
                initializedLocals.set(localIndex);
            }
        }
        return new BlockFrame(paramTypes, resultTypes, symbolTable, 0, initializedLocals, 0);
    }

    @Override
    int[] labelTypes() {
        return resultTypes();
    }

    @Override
    void exit(ParserState state, RuntimeBytecodeGen bytecode) {
        exitBlock(bytecode);
        registerDelegateContinuationFixups(state, -1);
    }

    protected void exitBlock(RuntimeBytecodeGen bytecode) {
        if (labelFixups.isEmpty()) {
            return;
        }
        final int location = bytecode.addLabel(resultTypeLength(), initialStackSize(), commonResultType(), legacyCatchDepth());
        for (BytecodeFixup labelFixup : labelFixups) {
            labelFixup.patch(location);
        }
    }

    @Override
    void addLabelFixup(BytecodeFixup fixup) {
        labelFixups.add(fixup);
    }
}
