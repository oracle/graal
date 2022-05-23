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
 * Represents an entry that refers to a different location in the byte code and extra data.
 */
public abstract class BranchTarget extends ExtraDataEntry {
    private final int byteCodeOffset;
    private final int extraDataOffset;

    private final int extraDataIndex;

    private int byteCodeDisplacement;
    private int extraDataDisplacement;
    private int extraDataTargetIndex;

    private boolean compactExtraDataTarget;

    protected BranchTarget(ExtraDataFormatHelper formatHelper, int byteCodeOffset, int extraDataOffset, int extraDataIndex) {
        super(formatHelper);
        this.compactExtraDataTarget = true;
        this.byteCodeOffset = byteCodeOffset;
        this.extraDataOffset = extraDataOffset;
        this.extraDataIndex = extraDataIndex;
    }

    /**
     * Sets the information about the branch target.
     * 
     * @param byteCodeTarget The target in the byte code
     * @param extraDataTarget The target location in the extra data array
     * @param extraDataTargetIndex The target index in the extra data list
     */
    public void setTargetInfo(int byteCodeTarget, int extraDataTarget, int extraDataTargetIndex) {
        this.byteCodeDisplacement = byteCodeTarget - byteCodeOffset;
        this.extraDataDisplacement = extraDataTarget - extraDataOffset;
        this.extraDataTargetIndex = extraDataTargetIndex;
        if (ExtraDataUtil.exceedsSignedShortValueWithIndicator(this.extraDataDisplacement) || ExtraDataUtil.exceedsSignedShortValue(this.byteCodeDisplacement)) {
            if (ExtraDataUtil.exceedsSignedIntValueWithIndicator(this.extraDataDisplacement)) {
                throw WasmException.create(Failure.NON_REPRESENTABLE_EXTRA_DATA_VALUE);
            }
            extendFormat();
            this.compactExtraDataTarget = false;
        }
    }

    protected int compactByteCodeDisplacement() {
        // Needed to correctly convert negative values
        return Short.toUnsignedInt((short) byteCodeDisplacement);
    }

    protected int extendedByteCodeDisplacement() {
        return byteCodeDisplacement;
    }

    protected int compactExtraDataDisplacement() {
        // Needed to correctly convert negative values
        return Short.toUnsignedInt((short) extraDataDisplacement);
    }

    protected int extendedExtraDataDisplacement() {
        return extraDataDisplacement;
    }

    /**
     * @return The index of the branch target in the extra data list
     */
    public int extraDataTargetIndex() {
        return extraDataTargetIndex;
    }

    public int extraDataIndex() {
        return extraDataIndex;
    }

    public void updateExtraDataDisplacement(int offset, int targetOffset) {
        extraDataDisplacement = targetOffset - offset;
        if (compactExtraDataTarget && ExtraDataUtil.exceedsSignedShortValueWithIndicator(extraDataDisplacement)) {
            if (ExtraDataUtil.exceedsSignedIntValueWithIndicator(extraDataDisplacement)) {
                throw WasmException.create(Failure.NON_REPRESENTABLE_EXTRA_DATA_VALUE);
            }
            extendFormat();
            compactExtraDataTarget = false;
        }
    }
}
