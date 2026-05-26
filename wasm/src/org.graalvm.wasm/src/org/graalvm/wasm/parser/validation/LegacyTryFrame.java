/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.parser.bytecode.BytecodeFixup;
import org.graalvm.wasm.parser.bytecode.RuntimeBytecodeGen;

public final class LegacyTryFrame extends ControlFrame {
    /** Fixups that transfer normal control to the legacy try label. */
    private final ArrayList<BytecodeFixup> branchLabelFixups;
    /** Exception handlers guarding the protected region of the legacy try. */
    private final ArrayList<ExceptionHandler> protectedRegionHandlers;
    /** Parent frame used to reset local-initialized status. */
    private final ControlFrame parentFrame;
    /** First bytecode offset protected by this try. */
    private final int protectedRegionStart;
    /** First bytecode offset after the protected region. */
    private int protectedRegionEnd = -1;
    /** Whether we are currently in a catch clause. */
    private boolean inCatchClause = false;

    LegacyTryFrame(int[] paramTypes, int[] resultTypes, int initialStackSize, ControlFrame parentFrame, int protectedRegionStart) {
        super(paramTypes, resultTypes, parentFrame.getSymbolTable(), initialStackSize, (BitSet) parentFrame.initializedLocals.clone(), parentFrame.legacyCatchDepth());
        this.branchLabelFixups = new ArrayList<>();
        this.protectedRegionHandlers = new ArrayList<>();
        this.parentFrame = parentFrame;
        this.protectedRegionStart = protectedRegionStart;
    }

    @Override
    int[] labelTypes() {
        return resultTypes();
    }

    boolean inCatchClause() {
        return inCatchClause;
    }

    @Override
    int legacyCatchDepth() {
        return super.legacyCatchDepth() + (inCatchClause() ? 1 : 0);
    }

    @Override
    void exit(ParserState state, RuntimeBytecodeGen bytecode) {
        if (inCatchClause()) {
            exitCatchClause(state, bytecode, false);
        } else {
            exitProtectedRegion(state, bytecode, false);
        }

        if (!branchLabelFixups.isEmpty()) {
            // bytecode label reached by branches targeting the try label
            int exitLabelLocation = bytecode.addLabel(resultTypeLength(), initialStackSize(), commonResultType(), legacyCatchDepth());
            for (BytecodeFixup labelFixup : branchLabelFixups) {
                labelFixup.patch(exitLabelLocation);
            }
        }

        final ExceptionTable table = createExceptionTable();
        if (table != null) {
            final int tableIndex = state.registerExceptionTable(table);
            registerDelegateContinuationFixups(state, tableIndex);
        } else {
            registerDelegateContinuationFixups(state, -1);
        }
    }

    @Override
    void addLabelFixup(BytecodeFixup fixup) {
        branchLabelFixups.add(fixup);
    }

    void exitProtectedRegion(ParserState state, RuntimeBytecodeGen bytecode, boolean hasMoreClauses) {
        assert protectedRegionEnd == -1 : "legacy try protected region already closed";
        if (hasMoreClauses) {
            state.checkStackAfterFrameExit(this);
            addLabelFixup(state.createBranchFixup(RuntimeBytecodeGen.BranchOp.BR));
        }
        protectedRegionEnd = bytecode.location();
    }

    void enterCatchClause(ParserState state, RuntimeBytecodeGen bytecode, int opcode, int tag, int[] paramTypes) {
        if (!inCatchClause()) {
            // Entering first catch.
            exitProtectedRegion(state, bytecode, true);
        } else {
            exitCatchClause(state, bytecode, true);
        }
        initializedLocals = (BitSet) parentFrame.initializedLocals.clone();
        inCatchClause = true;
        final int catchLabel = bytecode.addLabel(paramTypes.length, initialStackSize(), WasmType.getCommonValueType(paramTypes), legacyCatchDepth());
        final ExceptionHandler handler = new ExceptionHandler(opcode, tag, catchLabel);
        addProtectedRegionHandler(handler);
        // Since catch is a separate block the unreachable state has to be reset.
        resetUnreachable();
    }

    void exitCatchClause(ParserState state, RuntimeBytecodeGen bytecode, boolean hasMoreClauses) {
        assert inCatchClause() : "legacy try catch clause not active";
        if (hasMoreClauses) {
            state.checkStackAfterFrameExit(this);
        }
        bytecode.addOp(Bytecode.MISC);
        bytecode.addOp(Bytecode.LEGACY_CATCH_DROP);
        if (hasMoreClauses) {
            addLabelFixup(state.createBranchFixup(RuntimeBytecodeGen.BranchOp.BR));
        }
        inCatchClause = false;
    }

    void addProtectedRegionHandler(ExceptionHandler handler) {
        protectedRegionHandlers.add(handler);
    }

    ExceptionTable createExceptionTable() {
        if (protectedRegionHandlers.isEmpty()) {
            return null;
        }
        assert protectedRegionEnd != -1 : "legacy try handler range not closed";
        ExceptionTable table = new ExceptionTable(protectedRegionStart, protectedRegionEnd, protectedRegionHandlers.toArray(ExceptionHandler[]::new));
        return table;
    }
}
