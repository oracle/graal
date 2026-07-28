/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * Represents the scope of a block structure during module validation.
 */
public abstract class ControlFrame {
    private final int[] paramTypes;
    private final int[] resultTypes;
    private final SymbolTable symbolTable;

    private final int initialStackSize;
    private final int legacyCatchDepth;
    private boolean unreachable;
    private final int commonResultType;
    protected BitSet initializedLocals;
    private ArrayList<BytecodeFixup> delegateFixups;

    /**
     * @param paramTypes The parameter value types of the block structure.
     * @param resultTypes The result value types of the block structure.
     * @param symbolTable Necessary to look up the definitions of types in {@code paramTypes} and
     *            {@code resultTypes}
     * @param initialStackSize The size of the value stack when entering this block structure.
     * @param initializedLocals The set of locals which are already initialized at the start of this
     *            function
     */
    ControlFrame(int[] paramTypes, int[] resultTypes, SymbolTable symbolTable, int initialStackSize, BitSet initializedLocals, int legacyCatchDepth) {
        this.paramTypes = paramTypes;
        this.resultTypes = resultTypes;
        this.symbolTable = symbolTable;
        this.initialStackSize = initialStackSize;
        this.legacyCatchDepth = legacyCatchDepth;
        this.unreachable = false;
        commonResultType = WasmType.getCommonValueType(resultTypes);
        this.initializedLocals = (BitSet) initializedLocals.clone();
    }

    protected int[] paramTypes() {
        return paramTypes;
    }

    public int[] resultTypes() {
        return resultTypes;
    }

    protected int resultTypeLength() {
        return resultTypes.length;
    }

    protected SymbolTable getSymbolTable() {
        return symbolTable;
    }

    /**
     * @return The union of all result types.
     */
    protected int commonResultType() {
        return commonResultType;
    }

    /**
     * @return The types that must be on the value stack when branching to this frame.
     */
    abstract int[] labelTypes();

    protected int labelTypeLength() {
        return labelTypes().length;
    }

    int initialStackSize() {
        return initialStackSize;
    }

    int legacyCatchDepth() {
        return legacyCatchDepth;
    }

    void setUnreachable() {
        this.unreachable = true;
    }

    boolean isUnreachable() {
        return unreachable;
    }

    protected void resetUnreachable() {
        this.unreachable = false;
    }

    boolean isLocalInitialized(int localIndex) {
        return initializedLocals.get(localIndex);
    }

    void initializeLocal(int localIndex) {
        initializedLocals.set(localIndex);
    }

    /**
     * Performs checks and actions when exiting a frame.
     */
    abstract void exit(ParserState state, RuntimeBytecodeGen bytecode);

    /**
     * Adds a fixup that targets this control frame's label. The fixup is patched immediately when
     * the label is already available, or deferred until the frame emits its label.
     * 
     * @param fixup The fixup that targets this frame's label.
     */
    abstract void addLabelFixup(BytecodeFixup fixup);

    /**
     * Adds a fixup used by legacy {@code delegate} to target this control frame. The fixup is
     * patched with the exception-table location where exception dispatch should continue once
     * control is delegated to this frame.
     *
     * @param fixup The fixup to patch with this frame's delegate continuation location.
     */
    void addDelegateFixup(BytecodeFixup fixup) {
        if (delegateFixups == null) {
            delegateFixups = new ArrayList<>();
        }
        delegateFixups.add(fixup);
    }

    protected void registerDelegateContinuationFixups(ParserState state, int tableIndex) {
        if (delegateFixups != null) {
            for (BytecodeFixup fixup : delegateFixups) {
                state.addExceptionTableContinuationFixup(tableIndex, fixup);
            }
        }
    }
}
