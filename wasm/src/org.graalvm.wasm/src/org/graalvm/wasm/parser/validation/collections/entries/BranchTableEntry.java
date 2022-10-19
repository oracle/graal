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
 * Represents a br_table entry in the extra data list.
 * <p>
 * Compact format:
 * <p>
 * <ul>
 * <li>compactFormatIndicator (1-bit)
 * <li>size (unsigned 15-bit)
 * <li>profileCounter (unsigned 16-bit)
 * <li>(branchEntry {@link ConditionalBranchEntry})*
 * </ul>
 * <p>
 * Extended format:
 * <p>
 * <ul>
 * <li>extendedFormatIndicator (1-bit)
 * <li>size (unsigned 31-bit)
 * <li>unused (16-bit)
 * <li>profileCounter (unsigned 16-bit)
 * <li>(extendedBranchEntry {@link ConditionalBranchEntry})*
 * </ul>
 */
public class BranchTableEntry extends ExtraDataEntry implements ExtraDataFormatHelper {
    private final ConditionalBranchEntry[] items;

    public BranchTableEntry(int elementCount, ExtraDataFormatHelper formatHelper, int byteCodeOffset, int extraDataOffset, int extraDataIndex) {
        super(formatHelper);
        if (ExtraDataUtil.exceedsUnsignedShortValueWithIndicator(elementCount)) {
            if (ExtraDataUtil.exceedsUnsignedIntValueWithIndicator(elementCount)) {
                throw WasmException.create(Failure.NON_REPRESENTABLE_EXTRA_DATA_VALUE);
            }
            extendFormat();
        }
        this.items = new ConditionalBranchEntry[elementCount];
        for (int i = 0; i < elementCount; i++) {
            this.items[i] = new ConditionalBranchEntry(this, byteCodeOffset, extraDataOffset, extraDataIndex, 0);
        }
    }

    @Override
    public void extendExtraDataFormat() {
        extendFormat();
    }

    @Override
    protected int generateCompactData(int[] extraData, int entryOffset) {
        int offset = entryOffset;
        offset += ExtraDataUtil.addCompactTableHeader(extraData, offset, items.length);
        for (ConditionalBranchEntry item : items) {
            offset = item.generateCompactData(extraData, offset);
        }
        return offset;
    }

    @Override
    protected int generateExtendedData(int[] extraData, int entryOffset) {
        int offset = entryOffset;
        offset += ExtraDataUtil.addExtendedTableHeader(extraData, offset, items.length);
        offset += ExtraDataUtil.addProfileCounter(extraData, offset);
        for (ConditionalBranchEntry item : items) {
            offset = item.generateExtendedData(extraData, offset);
        }
        return offset;
    }

    @Override
    public int compactLength() {
        int size = ExtraDataUtil.COMPACT_TABLE_HEADER_SIZE;
        for (ConditionalBranchEntry item : items) {
            size += item.compactLength();
        }
        return size;
    }

    @Override
    public int extendedLength() {
        int size = ExtraDataUtil.EXTENDED_TABLE_HEADER_SIZE + ExtraDataUtil.PROFILE_SIZE;
        for (ConditionalBranchEntry item : items) {
            size += item.extendedLength();
        }
        return size;
    }

    public BranchTargetWithStackChange item(int index) {
        return items[index];
    }

    /**
     *
     * @param index The index of the branch item
     * @param unwindType The new unwind type
     * @return The updated item
     */
    public BranchTargetWithStackChange updateItemUnwindType(int index, int unwindType) {
        items[index].updateUnwindType(unwindType);
        return items[index];
    }

    public int size() {
        return items.length;
    }
}
