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

package org.graalvm.wasm.parser.validation.collections.entries;

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.validation.collections.ExtraDataFormatHelper;
import org.graalvm.wasm.util.ExtraDataUtil;

/**
 * Represents and entry that changes the number of values on the values stack and potentially
 * returns a value.
 */
public abstract class BranchTargetWithStackChange extends BranchTarget {

    private int unwindType;
    private int resultCount;
    private int stackSize;

    protected BranchTargetWithStackChange(ExtraDataFormatHelper formatHelper, int byteCodeOffset, int extraDataOffset, int extraDataIndex, int unwindType) {
        super(formatHelper, byteCodeOffset, extraDataOffset, extraDataIndex);
        this.unwindType = unwindType;
    }

    /**
     * Sets the information about the stack change.
     *
     * @param returnUnwindType The unwind type
     * @param resultCount The number of result values
     * @param stackSize The stack size after the jump
     */
    public void setStackInfo(int returnUnwindType, int resultCount, int stackSize) {
        this.unwindType |= returnUnwindType;
        this.resultCount = resultCount;
        this.stackSize = stackSize;
        assert ExtraDataUtil.isValidUnwindType(returnUnwindType) : "Invalid value type indicator";
        if (ExtraDataUtil.exceedsUnsigned7BitValue(resultCount) || ExtraDataUtil.exceedsUnsigned7BitValue(stackSize)) {
            if (ExtraDataUtil.exceedsPositiveIntValue(resultCount) || ExtraDataUtil.exceedsPositiveIntValue(stackSize)) {
                throw WasmException.create(Failure.NON_REPRESENTABLE_EXTRA_DATA_VALUE);
            }
            extendFormat();
        }
    }

    void updateUnwindType(int updatedUnwindType) {
        this.unwindType |= updatedUnwindType;
    }

    protected int unwindType() {
        return unwindType;
    }

    protected int resultCount() {
        return resultCount;
    }

    protected int stackSize() {
        return stackSize;
    }
}
