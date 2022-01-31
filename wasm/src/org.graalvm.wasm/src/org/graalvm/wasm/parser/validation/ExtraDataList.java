/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_BYTECODE_INDEX;
import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_EXTRA_INDEX;
import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_IF_BYTECODE_INDEX;
import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_IF_EXTRA_INDEX;
import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_IF_LENGTH;
import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_IF_STACK_INFO;
import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_STACK_INFO;
import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_TABLE_ENTRY_LENGTH;
import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_TABLE_ENTRY_OFFSET;
import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_TABLE_SIZE;
import static org.graalvm.wasm.constants.ExtraDataOffsets.CALL_NODE_INDEX;
import static org.graalvm.wasm.constants.ExtraDataOffsets.CALL_INDIRECT_NODE_INDEX;
import static org.graalvm.wasm.constants.ExtraDataOffsets.CALL_INDIRECT_LENGTH;
import static org.graalvm.wasm.constants.ExtraDataOffsets.CALL_LENGTH;
import static org.graalvm.wasm.constants.ExtraDataOffsets.ELSE_BYTECODE_INDEX;
import static org.graalvm.wasm.constants.ExtraDataOffsets.ELSE_EXTRA_INDEX;
import static org.graalvm.wasm.constants.ExtraDataOffsets.ELSE_LENGTH;
import static org.graalvm.wasm.constants.ExtraDataOffsets.IF_BYTECODE_INDEX;
import static org.graalvm.wasm.constants.ExtraDataOffsets.IF_EXTRA_INDEX;
import static org.graalvm.wasm.constants.ExtraDataOffsets.IF_LENGTH;
import static org.graalvm.wasm.constants.ExtraDataOffsets.STACK_INFO_RETURN_LENGTH_SHIFT;
import static org.graalvm.wasm.constants.ExtraDataOffsets.STACK_INFO_STACK_SIZE_MASK;
import static org.graalvm.wasm.constants.ExtraDataOffsets.BR_LENGTH;

public class ExtraDataList {

    private static final int[] EMPTY_EXTRA_DATA = new int[0];

    private int[] extraData;

    private int extraDataCount;

    public ExtraDataList() {
        this.extraData = new int[4];
        this.extraDataCount = 0;
    }

    public int addIfLocation() {
        ensureExtraDataSize(IF_LENGTH);
        // initializes condition profile to 0.
        int location = extraDataCount;
        extraDataCount += IF_LENGTH;
        return location;
    }

    public void setIfTarget(int location, int target, int extraTarget) {
        extraData[location + IF_BYTECODE_INDEX] = target;
        extraData[location + IF_EXTRA_INDEX] = extraTarget;
    }

    public int addElseLocation() {
        ensureExtraDataSize(ELSE_LENGTH);
        int location = extraDataCount;
        extraDataCount += ELSE_LENGTH;
        return location;
    }

    public void setElseTarget(int location, int target, int extraTarget) {
        extraData[location + ELSE_BYTECODE_INDEX] = target;
        extraData[location + ELSE_EXTRA_INDEX] = extraTarget;
    }

    public int addConditionalBranchLocation() {
        ensureExtraDataSize(BR_IF_LENGTH);
        // initializes condition profile with 0.
        int location = extraDataCount;
        extraDataCount += BR_IF_LENGTH;
        return location;
    }

    public void setConditionalBranchTarget(int location, int target, int extraTarget, int stackSize, int returnLength) {
        extraData[location + BR_IF_BYTECODE_INDEX] = target;
        extraData[location + BR_IF_EXTRA_INDEX] = extraTarget;
        extraData[location + BR_IF_STACK_INFO] = (stackSize & STACK_INFO_STACK_SIZE_MASK) + (returnLength << STACK_INFO_RETURN_LENGTH_SHIFT);
    }

    public int addUnconditionalBranchLocation() {
        ensureExtraDataSize(BR_LENGTH);
        int location = extraDataCount;
        extraDataCount += BR_LENGTH;
        return location;
    }

    public void setUnconditionalBranchTarget(int location, int target, int extraTarget, int stackSize, int returnLength) {
        extraData[location + BR_BYTECODE_INDEX] = target;
        extraData[location + BR_EXTRA_INDEX] = extraTarget;
        extraData[location + BR_STACK_INFO] = (stackSize & STACK_INFO_STACK_SIZE_MASK) + (returnLength << STACK_INFO_RETURN_LENGTH_SHIFT);
    }

    public int addBranchTableLocation(int size) {
        ensureExtraDataSize(BR_TABLE_ENTRY_OFFSET + BR_TABLE_ENTRY_LENGTH * size);
        extraData[extraDataCount + BR_TABLE_SIZE] = size;
        int location = extraDataCount;
        extraDataCount += BR_TABLE_ENTRY_OFFSET + BR_TABLE_ENTRY_LENGTH * size;
        return location;
    }

    public int addBranchTableEntry(int location, int index) {
        return location + BR_TABLE_ENTRY_OFFSET + index * BR_TABLE_ENTRY_LENGTH;
    }

    public void addIndirectCall(int nodeIndex) {
        ensureExtraDataSize(CALL_INDIRECT_LENGTH);
        extraData[extraDataCount + CALL_INDIRECT_NODE_INDEX] = nodeIndex;
        // initializes condition profile with 0.
        extraDataCount += CALL_INDIRECT_LENGTH;
    }

    public void addCall(int nodeIndex) {
        ensureExtraDataSize(CALL_LENGTH);
        extraData[extraDataCount + CALL_NODE_INDEX] = nodeIndex;
        extraDataCount += CALL_LENGTH;
    }

    public int getLocation() {
        return extraDataCount;
    }

    public int[] getExtraDataArray() {
        int[] result = new int[extraDataCount];
        if (extraData == null) {
            return EMPTY_EXTRA_DATA;
        } else {
            System.arraycopy(extraData, 0, result, 0, extraDataCount);
            return result;
        }
    }

    private void ensureExtraDataSize(int requiredSize) {
        int nextSizeFactor = 0;
        while (extraDataCount + requiredSize >= extraData.length << nextSizeFactor) {
            nextSizeFactor++;
        }
        if (nextSizeFactor != 0) {
            int[] updatedExtraData = new int[extraData.length << nextSizeFactor];
            System.arraycopy(extraData, 0, updatedExtraData, 0, extraDataCount);
            extraData = updatedExtraData;
        }
    }
}
