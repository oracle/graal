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

package org.graalvm.wasm.parser.validation.collections;

import java.util.ArrayList;

import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.parser.validation.collections.entries.BranchTableEntry;
import org.graalvm.wasm.parser.validation.collections.entries.CallEntry;
import org.graalvm.wasm.parser.validation.collections.entries.ConditionalBranchEntry;
import org.graalvm.wasm.parser.validation.collections.entries.ElseEntry;
import org.graalvm.wasm.parser.validation.collections.entries.ExtraDataEntry;
import org.graalvm.wasm.parser.validation.collections.entries.IfEntry;
import org.graalvm.wasm.parser.validation.collections.entries.IndirectCallEntry;
import org.graalvm.wasm.parser.validation.collections.entries.BranchTarget;
import org.graalvm.wasm.parser.validation.collections.entries.BranchTargetWithStackChange;
import org.graalvm.wasm.parser.validation.collections.entries.UnconditionalBranchEntry;

/**
 * Representation of extra data during module validation. Used to build up the extra data array.
 */
public class ExtraDataList implements ExtraDataFormatHelper {
    private static final int INITIAL_EXTRA_DATA_SIZE = 8;
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private final ArrayList<ExtraDataEntry> entries;
    private final ArrayList<BranchTarget> branchEntries;
    private final IntArrayList entryExtraDataOffsets;

    private int size;
    private boolean extend;

    public ExtraDataList() {
        this.entries = new ArrayList<>(INITIAL_EXTRA_DATA_SIZE);
        this.branchEntries = new ArrayList<>(0);
        this.entryExtraDataOffsets = new IntArrayList();
        this.size = 0;
        this.extend = false;
    }

    private void addEntry(ExtraDataEntry entry) {
        entries.add(entry);
        entryExtraDataOffsets.add(size);
        size += entry.length();
    }

    @Override
    public void extendExtraDataFormat() {
        this.extend = true;
    }

    public BranchTarget addIf(int offset) {
        IfEntry entry = new IfEntry(this, offset, size, entries.size());
        addEntry(entry);
        branchEntries.add(entry);
        return entry;
    }

    public BranchTarget addElse(int offset) {
        ElseEntry entry = new ElseEntry(this, offset, size, entries.size());
        addEntry(entry);
        branchEntries.add(entry);
        return entry;
    }

    public BranchTargetWithStackChange addConditionalBranch(int offset, int unwindType) {
        ConditionalBranchEntry entry = new ConditionalBranchEntry(this, offset, size, entries.size(), unwindType);
        addEntry(entry);
        branchEntries.add(entry);
        return entry;
    }

    public BranchTargetWithStackChange addUnconditionalBranch(int offset, int unwindType) {
        UnconditionalBranchEntry entry = new UnconditionalBranchEntry(this, offset, size, entries.size(), unwindType);
        addEntry(entry);
        branchEntries.add(entry);
        return entry;
    }

    public BranchTableEntry addBranchTable(int elementCount, int offset) {
        BranchTableEntry entry = new BranchTableEntry(elementCount, this, offset, size, entries.size());
        addEntry(entry);
        for (int i = 0; i < entry.size(); i++) {
            branchEntries.add(entry.item(i));
        }
        return entry;
    }

    public void addIndirectCall(int nodeIndex) {
        addEntry(new IndirectCallEntry(nodeIndex, this));
    }

    public void addCall(int nodeIndex) {
        addEntry(new CallEntry(nodeIndex, this));
    }

    /**
     * @return The next location in the extra data array
     */
    public int nextEntryLocation() {
        return size;
    }

    /**
     * @return The next index in the extra data list
     */
    public int nextEntryIndex() {
        return entries.size();
    }

    public int[] extraDataArray() {
        if (size == 0) {
            return EMPTY_INT_ARRAY;
        } else {
            int dataSize = size;
            if (extend) {
                int[] offsets = entryExtraDataOffsets.toArray();
                // Every size extensions can cause other size extensions (extra data displacement
                // changes). Therefore, we can only stop if no more extensions happened.
                // This algorithm is quadratic in the worst case, but should need fewer iterations
                // on average.
                while (extend) {
                    extend = false;
                    // Sync the offsets array with the new sizes of the entries for displacement
                    // calculations.
                    dataSize = 0;
                    for (int i = 0; i < entries.size(); i++) {
                        ExtraDataEntry entry = entries.get(i);
                        final int s = entry.length();
                        offsets[i] = dataSize;
                        dataSize += s;
                    }
                    // Update extra data displacements caused by entry size changes.
                    for (BranchTarget b : branchEntries) {
                        int targetIndex = b.extraDataTargetIndex();
                        int targetOffset = dataSize;
                        if (targetIndex < offsets.length) {
                            targetOffset = offsets[targetIndex];
                        }
                        b.updateExtraDataDisplacement(offsets[b.extraDataIndex()], targetOffset);
                    }
                }
            }
            int[] data = new int[dataSize];
            int entryOffset = 0;
            for (ExtraDataEntry entry : entries) {
                entryOffset = entry.generateData(data, entryOffset);
            }
            return data;
        }
    }
}
