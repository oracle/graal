/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.collection.IntArrayList;

/**
 * Represents the scope of a block structure during module validation.
 */
public abstract class ControlFrame {
    private final byte[] paramTypes;
    private final byte[] resultTypes;
    private final int initialStackSize;
    private boolean unreachable;
    private final IntArrayList conditionalBranches;
    private final IntArrayList unconditionalBranches;

    /**
     * @param paramTypes The parameter value types of the block structure.
     * @param resultTypes The result value types of the block structure.
     * @param initialStackSize The size of the value stack when entering this block structure.
     * @param unreachable If the block structure should be declared unreachable.
     */
    ControlFrame(byte[] paramTypes, byte[] resultTypes, int initialStackSize, boolean unreachable) {
        this.paramTypes = paramTypes;
        this.resultTypes = resultTypes;
        this.initialStackSize = initialStackSize;
        this.unreachable = unreachable;

        this.conditionalBranches = new IntArrayList();
        this.unconditionalBranches = new IntArrayList();
    }

    /**
     * @return The types that must be on the value stack when branching to this frame.
     */
    abstract byte[] getLabelTypes();

    /**
     * Performs checks and actions when entering an else branch.
     * 
     * @param state The current parser state.
     * @param extraData The current extra data array.
     * @param offset The offset of the else branch in the wasm binary.
     */
    abstract void enterElse(ParserState state, ExtraDataList extraData, int offset);

    /**
     * Performs checks and actions when exiting a frame.
     * 
     * @param extraData The current extra data array.
     * @param offset The offset of the end instruction in the wasm binary.
     */
    abstract void exit(ExtraDataList extraData, int offset);

    /**
     * Adds a conditional branch to this frame.
     * 
     * @param extraData The current extra data array.
     */
    void addConditionalBranch(ExtraDataList extraData) {
        conditionalBranches.add(extraData.addConditionalBranchLocation());
    }

    /**
     * Adds an unconditional branch to this frame.
     * 
     * @param extraData The current extra data array.
     */
    void addUnconditionalBranch(ExtraDataList extraData) {
        unconditionalBranches.add(extraData.addUnconditionalBranchLocation());
    }

    /**
     * Adds a conditional branch from a branch table entry to this fraem.
     * 
     * @param extraData The current extra data array.
     * @param location The location of the branch table in the extra data array.
     * @param index The index of the entry in the branch table.
     */
    void addBranchTableEntry(ExtraDataList extraData, int location, int index) {
        conditionalBranches.add(extraData.getBranchTableEntryLocation(location, index));
    }

    protected int[] conditionalBranches() {
        return conditionalBranches.toArray();
    }

    protected int[] unconditionalBranches() {
        return unconditionalBranches.toArray();
    }

    protected byte[] getParamTypes() {
        return paramTypes;
    }

    public byte[] getResultTypes() {
        return resultTypes;
    }

    protected int getLabelTypeLength() {
        return getLabelTypes().length;
    }

    int getInitialStackSize() {
        return initialStackSize;
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
}
