/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.genscavenge;

import java.nio.ByteBuffer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.HostedByteBufferPointer;
import com.oracle.svm.core.util.UnsignedUtils;

/**
 * A "first object table" to tell me the start of the first object that crosses onto a card
 * remembered set memory region.
 * <p>
 * The card table is a summary of pointer stores into a region of the heap. The card table is linear
 * with the region it summarizes, but smaller than the region. Each card table entry records that a
 * pointer has been stored in the address range summarized by the entry.
 * <p>
 * During a young generation collection, all the pointers on dirty cards must be examined to see if
 * they reference objects in the young generation, since those objects are reachable and must be
 * scavenged (and any pointers to them must be updated). In order to find the pointers within
 * objects I have to find the header of each object that has pointers in the covered region, so that
 * I can use the class to get to any interior pointers. Once I find the header for the first object
 * in a region I can stride by the size of the object to find the next object. But finding the first
 * header is hard because objects can cross the boundaries between the cards. The first object table
 * solves this problem by saying where is the start of the object that crosses onto the memory
 * covered by a card.
 * <p>
 * The table is linear with the memory it covers, so the table can be indexed by scaling an index
 * into the memory region, and the memory region can be indexed by scaling an index into the table.
 * In the code here, an entry is one byte for each memory region covered by an entry.
 * <p>
 * Entries come in two flavors. A memory offset entry gives an offset into the *previous* memory
 * region to the start of the object that crosses onto this entry. If all objects were tiny, then
 * the one byte entry could hold the memory offset back to the header for the object that crosses
 * onto this card. A memory offset entry can be scaled, because objects are 8-byte aligned in memory
 * so the entries can be in units of 8 bytes, which gives them larger range. Thus, if a table entry
 * covers 512 bytes of memory, the offsets into the previous card can have magnitudes in the range
 * [0..(512/8)], or [0..64]. In the code here, offsets into memory are represented by negative or
 * zero values: the number of 8-byte units before the start of the memory covered by this entry. For
 * example, an entry of -17 would indicate that the object in the memory corresponding to the
 * beginning of this entry starts (17 * 8 =) 136 bytes before the memory covered by this entry. (An
 * offset of 0 means the first object starts at the beginning of the memory covered by this entry.)
 * <p>
 * If an object is larger than can be represented by one byte of offset, the first entry contains
 * the memory offset to the start of the object, as above. Entries corresponding to the rest of the
 * object the object hold values that are index offsets to that first memory offset entry. In the
 * code here, such index offset entries are strictly positive values. Index offset entries come in
 * two flavors. Small positive values give a simple offset to the memory offset entry for the
 * object. Larger positive values give an exponent of a logarithmic offset to an entry closer to the
 * memory offset entry. Thus a small value, say 3, would say that 3 entries towards the beginning of
 * the object is the entry holding the memory offset to the start of the object. But a larger value,
 * say 42, would say that 2^17 entries towards the beginning of the object is an entry that either
 * holds the memory offset entry (as a negative value), or a table offset entry that gets us closer
 * to the memory offset entry (as a positive value). In order not to confuse small positive entry
 * offsets and small positive entry offset exponents, the exponents are biased by adding a value
 * that make then larger than any small entry offsets.
 *
 * To summarize, and using the values of the various tunable constants used in the code here, first
 * object table entries come in several ranges: <blockquote>
 * <table>
 * <tr>
 * <th align><u>Entry&nbsp;Value</u></th>
 * <th align=left><u> Interpretation</u></th>
 * </tr>
 * <tr>
 * <td>[-128..0]</td>
 * <td>A memory offset in 8-byte units from the address corresponding to the start of this entry to
 * the header of the object that crosses onto the memory corresponding to this entry.</td>
 * </tr>
 * <tr>
 * <td>[1..63]</td>
 * <td>A linear entry offset to the entry N entries to the left.</td>
 * </tr>
 * <tr>
 * <td>[64..126]</td>
 * <td>An exponential entry offset to the entry 2^(6+N-64) entries to the left.</td>
 * </tr>
 * <tr>
 * <td>127</td>
 * <td>An otherwise uninitialized entry.</td>
 * </tr>
 * </table>
 * </blockquote>
 *
 * A first object table entry is initialized when an object is allocated that crosses on to the
 * memory corresponding to a new entry.
 * <p>
 * Implementation note: Table entries are bytes but converted to and from ints with bounds checks.
 */
public final class FirstObjectTable {
    /**
     * The number of bytes of memory covered by an entry.
     *
     * Since the indexes into the CardTable are used to index into the FirstObjectTable, these need
     * to have the same value.
     */
    private static final int BYTES_COVERED_BY_ENTRY = CardTable.getBytesCoveredByEntry();

    private static final int ENTRY_SIZE_BYTES = 1;

    private static final int ENTRY_MIN = -128;
    private static final int ENTRY_MAX = 127;

    /**
     * Memory offsets are in the range [-128..MemoryOffsetMax]. That covers cards up to size
     * (MemoryOffsetScale * -MemoryOffsetMin), or I could use any extra bits for something else, at
     * the cost of some range checks.
     */
    private static final int MEMORY_OFFSET_MIN = ENTRY_MIN;
    private static final int MEMORY_OFFSET_MAX = 0;

    /**
     * Linear entry offsets are in the range (MemoryOffSetMax..LinearOffsetMax]. Exponential entry
     * offsets are in the range (LinearOffsetMax..UninitializedEntry).
     */
    private static final int LINEAR_OFFSET_MIN = MEMORY_OFFSET_MAX + 1;
    private static final int LINEAR_OFFSET_MAX = 63;

    /** The first exponential entry offset is 2^6, where 6 is log2(LinearOffsetLimit). */
    private static final int EXPONENT_MIN = 6;
    private static final int EXPONENT_MAX = 55;

    /**
     * The bias for exponential entry offsets to distinguish them from linear offset entries.
     * 
     * The limit for {@link #EXPONENT_MAX} seems to be that for the maximal object of 2^64 bytes, I
     * never have to skip back more than (2^64)/{@link #ENTRY_SIZE_BYTES} entries, so the maximum
     * exponent I'll need is 55. So the EXPONENT_BIAS just has to leave enough room for that many
     * biased values between {@link #LINEAR_OFFSET_MAX} and {@link #UNINITIALIZED_ENTRY}. With
     * {@link #LINEAR_OFFSET_MAX} at 63 and {@link #UNINITIALIZED_ENTRY} at 127, I think that means
     * EXPONENT_BIAS could be 71, leaving room for 8 special entry values in the open interval
     * ({@link #LINEAR_OFFSET_MAX}..EXPONENT_BIAS), or at the high end next to
     * {@link #UNINITIALIZED_ENTRY}.
     *
     * In fact, the current largest object that can be allocated is an array of long (or double, or
     * Object) of size Integer.MAX_VALUE, so the largest skip back is (2^35)/512 or 2^24. That
     * leaves lots of room for special values.
     * 
     */
    private static final int EXPONENT_BIAS = 1 + LINEAR_OFFSET_MAX - EXPONENT_MIN;

    private static final int UNINITIALIZED_ENTRY = 127;

    private FirstObjectTable() {
    }

    static Pointer initializeTableToLimit(Pointer table, Pointer tableLimit) {
        UnsignedWord indexLimit = FirstObjectTable.tableOffsetToIndex(tableLimit.subtract(table));
        return FirstObjectTable.initializeTableToIndex(table, indexLimit);
    }

    static boolean isUninitializedIndex(Pointer table, UnsignedWord index) {
        int entry = getEntryAtIndex(table, index);
        return isUninitializedEntry(entry);
    }

    static void setTableForObject(Pointer table, Pointer memory, Pointer start, Pointer end) {
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        setTableForObjectUnchecked(table, memory, start, end);
    }

    private static void setTableForObjectUnchecked(Pointer table, Pointer memory, Pointer start, Pointer end) {
        assert memory.belowOrEqual(start);
        assert start.belowThan(end);
        UnsignedWord startOffset = start.subtract(memory);
        /* The argument "end" is just past the real end of the object, so back it up one byte. */
        UnsignedWord endOffset = end.subtract(1).subtract(memory);
        setTableForObjectAtLocation(table, startOffset, endOffset);
    }

    private static void setTableForObjectAtLocation(Pointer table, UnsignedWord startOffset, UnsignedWord endOffset) {
        UnsignedWord startIndex = memoryOffsetToIndex(startOffset);
        UnsignedWord endIndex = memoryOffsetToIndex(endOffset);
        UnsignedWord startMemoryOffset = indexToMemoryOffset(startIndex);
        if (startIndex.equal(endIndex) && startOffset.unsignedRemainder(BYTES_COVERED_BY_ENTRY).notEqual(0)) {
            /* The object does not cross, or start on, a card boundary: nothing to do. */
            return;
        }
        UnsignedWord memoryOffsetIndex;
        if (startOffset.equal(startMemoryOffset)) {
            memoryOffsetIndex = startIndex;
            setEntryAtIndex(table, memoryOffsetIndex, 0);
        } else {
            /*
             * The startOffset is in the middle of the memory for startIndex. That is, before the
             * memory for startIndex+1.
             */
            memoryOffsetIndex = startIndex.add(1);
            UnsignedWord memoryIndexOffset = indexToMemoryOffset(memoryOffsetIndex);
            int entry = memoryOffsetToEntry(memoryIndexOffset.subtract(startOffset));
            setEntryAtIndex(table, memoryOffsetIndex, entry);
        }
        /*
         * Fill from memoryOffsetIndex towards endIndex with offset entries referring back to
         * memoryOffsetIndex.
         */
        /* First, as many linear offsets as are needed, or as I can have. */
        UnsignedWord linearIndexMax = UnsignedUtils.min(endIndex, memoryOffsetIndex.add(LINEAR_OFFSET_MAX));
        UnsignedWord entryIndex = memoryOffsetIndex.add(1);
        int entry = LINEAR_OFFSET_MIN;
        while (entryIndex.belowOrEqual(linearIndexMax)) {
            setEntryAtIndex(table, entryIndex, entry);
            entryIndex = entryIndex.add(1);
            entry++;
        }
        /* Next, as many exponential offsets as are needed. */
        int unbiasedExponent = EXPONENT_MIN;
        while (entryIndex.belowOrEqual(endIndex)) {
            /* There are 2^N entries with the exponent N. */
            for (int count = 0; count < (1 << unbiasedExponent); count += 1) {
                int biasedEntry = biasExponent(unbiasedExponent);
                setEntryAtIndex(table, entryIndex, biasedEntry);
                entryIndex = entryIndex.add(1);
                if (entryIndex.aboveThan(endIndex)) {
                    break;
                }
            }
            unbiasedExponent += 1;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static void setTableInBufferForObject(ByteBuffer buffer, int bufferTableOffset, UnsignedWord startOffset, UnsignedWord endOffset) {
        UnsignedWord actualEndOffset = endOffset.subtract(1); // methods wants offset of last byte
        setTableForObjectAtLocation(new HostedByteBufferPointer(buffer, bufferTableOffset), startOffset, actualEndOffset);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static void initializeTableInBuffer(ByteBuffer buffer, int bufferTableOffset, UnsignedWord tableSize) {
        doInitializeTableToLimit(new HostedByteBufferPointer(buffer, bufferTableOffset), tableSize);
    }

    /**
     * Return a Pointer to an object that could be precisely marked by this card.
     *
     * For a precise mark, this means I have to back up to the start of the object that crosses onto
     * (or starts) this entry.
     *
     * @param tableStart The start of the first object table.
     * @param memoryStart The start of the memory region corresponding to the first object table.
     * @param memoryLimit The end of the memory region corresponding to the first object table.
     * @param index The index into the first object table.
     * @return A Pointer to first object that could be precisely marked at this index.
     */
    static Pointer getPreciseFirstObjectPointer(Pointer tableStart, Pointer memoryStart, Pointer memoryLimit, UnsignedWord index) {
        Log trace = Log.noopLog().string("[FirstObjectTable.getPreciseFirstObjectPointer:").string("  index: ").unsigned(index);
        UnsignedWord currentIndex = index;
        int currentEntry = getEntryAtIndex(tableStart, currentIndex);
        if (trace.isEnabled()) {
            trace.string("  entry: ");
            entryToLog(trace, currentEntry);
        }
        assert (currentEntry != UNINITIALIZED_ENTRY) : "uninitialized first object table entry";
        if (MEMORY_OFFSET_MAX < currentEntry) {
            /* The current entry is an entry offset. */
            while (LINEAR_OFFSET_MAX < currentEntry) {
                /* The current entry is a exponential offset. */
                int exponent = unbiasExponent(currentEntry);
                UnsignedWord deltaIndex = exponentToOffset(exponent);
                assert deltaIndex.belowOrEqual(currentIndex) : "Delta out of bounds.";
                currentIndex = currentIndex.subtract(deltaIndex);
                currentEntry = getEntryAtIndex(tableStart, currentIndex);
            }
            if (MEMORY_OFFSET_MAX < currentEntry) {
                /* The current entry is a linear offset. */
                currentIndex = currentIndex.subtract(currentEntry);
                currentEntry = getEntryAtIndex(tableStart, currentIndex);
            }
        }
        /* The current entry is a memory offset. */
        Pointer result = getPointerAtOffset(memoryStart, currentIndex, currentEntry);
        trace.string("  returns: ").hex(result);
        assert memoryStart.belowOrEqual(result) : "memoryStart.belowOrEqual(result)";
        assert result.belowThan(memoryLimit) : "result.belowThan(memoryLimit)";
        trace.string("]").newline();
        return result;
    }

    /**
     * Return a Pointer into a memory region indicated by the entry at a given index.
     *
     * For an imprecise mark, this means I can skip past any object that crosses onto this entry.
     *
     * @param tableStart The start of the first object table.
     * @param memoryStart The start of the memory region corresponding to the first object table.
     * @param memoryLimit The end of the memory region corresponding to the first object table.
     * @param index The index into the first object table.
     * @return A Pointer to the first object that could be imprecisely marked at this index. Returns
     *         the null Pointer if no object could be imprecisely marked at this index, e.g., if the
     *         object that crosses onto this card either also crosses off of this card, or runs up
     *         to the memory limit.
     */
    static Pointer getImpreciseFirstObjectPointer(Pointer tableStart, Pointer memoryStart, Pointer memoryLimit, UnsignedWord index) {
        Log trace = Log.noopLog().string("[FirstObjectTable.getImpreciseFirstObjectPointer:");
        trace.string("  tableStart: ").hex(tableStart).string("  memoryStart: ").hex(memoryStart).string("  memoryLimit: ").hex(memoryLimit).string("  index: ").unsigned(index).newline();
        Pointer preciseFirstPointer = getPreciseFirstObjectPointer(tableStart, memoryStart, memoryLimit, index);
        /* If the object starts before the memory for this index, skip over it. */
        Pointer indexedMemoryStart = memoryStart.add(indexToMemoryOffset(index));
        Pointer result;
        if (preciseFirstPointer.belowThan(indexedMemoryStart)) {
            Object crossingObject = preciseFirstPointer.toObject();
            result = LayoutEncoding.getObjectEnd(crossingObject);
        } else {
            assert preciseFirstPointer.equal(indexedMemoryStart) : "preciseFirstPointer.equal(indexedMemoryStart)";
            result = indexedMemoryStart;
        }
        trace.string("  returns: ").hex(result);
        assert memoryStart.belowOrEqual(result) : "memoryStart.belowOrEqual(result)";
        assert result.belowOrEqual(memoryLimit) : "result.belowOrEqual(memoryLimit)";
        trace.string("]").newline();
        return result;
    }

    /** Walk the table checking I can find the start of each object that crosses onto an entry. */
    static boolean verify(Pointer tableStart, Pointer memoryStart, Pointer memoryLimit) {
        Log trace = HeapVerifier.getTraceLog().string("[FirstObjectTable.verify:");
        trace.string("  tableStart: ").hex(tableStart).string("  memoryStart: ").hex(memoryStart).string("  memoryLimit: ").hex(memoryLimit);
        UnsignedWord indexLimit = getTableSizeForMemoryRange(memoryStart, memoryLimit);
        for (UnsignedWord index = WordFactory.unsigned(0); index.belowThan(indexLimit); index = index.add(1)) {
            trace.newline().string("  FirstObjectTable.verify: index: ").unsigned(index).newline();
            Pointer objStart = getPreciseFirstObjectPointer(tableStart, memoryStart, memoryLimit, index);
            trace.string("  objStart: ").hex(objStart).newline();
            if (objStart.belowThan(memoryStart)) {
                trace.string("  FirstObjectTable.verify: objStart.belowThan(memoryStart) => false]").newline();
                return false;
            }
            if (memoryLimit.belowOrEqual(objStart)) {
                trace.string("  FirstObjectTable.verify: memoryLimit.belowOrEqual(objStart) => false]").newline();
                return false;
            }
            if (!HeapImpl.getHeapImpl().getHeapVerifier().verifyObjectAt(objStart)) {
                Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[FirstObjectTable.verify:");
                witness.string("  tableStart: ").hex(tableStart).string("  memoryStart: ").hex(memoryStart).string("  memoryLimit: ").hex(memoryLimit);
                witness.string("  objStart: ").hex(objStart).string("  fails to verify").string("]").newline();
                return false;
            }
            /* Check that the object crosses onto this entry. */
            Pointer entryStart = memoryStart.add(indexToMemoryOffset(index));
            trace.string("  entryStart: ").hex(entryStart);
            if (!(objStart.belowOrEqual(entryStart))) {
                Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[FirstObjectTable.verify:");
                witness.string("  tableStart: ").hex(tableStart).string("  memoryStart: ").hex(memoryStart).string("  memoryLimit: ").hex(memoryLimit);
                witness.string("  objStart: ").hex(objStart).string("  entryStart: ").hex(entryStart).string("  !(objStart.belowOrEqual(entryStart))").string("]").newline();
                return false;
            }
            Object obj = objStart.toObject();
            Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
            trace.string("  ");
            trace.string(obj.getClass().getName());
            trace.string("  objEnd: ").hex(objEnd);
            if (!(entryStart.belowThan(objEnd))) {
                Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[FirstObjectTable.verify:");
                witness.string("  tableStart: ").hex(tableStart).string("  memoryStart: ").hex(memoryStart).string("  memoryLimit: ").hex(memoryLimit);
                witness.string("  objEnd: ").hex(objEnd).string("  entryStart: ").hex(entryStart).string("  !(entryStart.belowThan(objEnd))").string("]").newline();
                return false;
            }
        }
        trace.string("  => true]").newline();
        return true;
    }

    static UnsignedWord getTableSizeForMemoryRange(Pointer memoryStart, Pointer memoryLimit) {
        assert memoryStart.belowOrEqual(memoryLimit) : "Pointers out of order";
        UnsignedWord memorySize = memoryLimit.subtract(memoryStart);
        return getTableSizeForMemorySize(memorySize);
    }

    private static UnsignedWord getTableSizeForMemorySize(UnsignedWord memorySize) {
        UnsignedWord roundedMemory = UnsignedUtils.roundUp(memorySize, WordFactory.unsigned(BYTES_COVERED_BY_ENTRY));
        UnsignedWord index = FirstObjectTable.memoryOffsetToIndex(roundedMemory);
        return index.multiply(ENTRY_SIZE_BYTES);
    }

    /**
     * The multiplier from memory offsets to byte offsets into the previous card. This is the
     * granularity to which I can point to the start of an object.
     */
    private static int memoryOffsetScale() {
        return ConfigurationValues.getObjectLayout().getAlignment();
    }

    private static Pointer initializeTableToIndex(Pointer table, UnsignedWord indexLimit) {
        /*
         * The table doesn't really need to be initialized, but if I'm making assertions, then I
         * should initialize the entries to the uninitialized value.
         */
        assert doInitializeTableToLimit(table, indexLimit);
        return table;
    }

    private static void doInitializeTableToLimit(Pointer table, Pointer tableLimit) {
        UnsignedWord indexLimit = FirstObjectTable.tableOffsetToIndex(tableLimit.subtract(table));
        doInitializeTableToLimit(table, indexLimit);
    }

    private static boolean doInitializeTableToLimit(Pointer table, UnsignedWord indexLimit) {
        for (UnsignedWord index = WordFactory.unsigned(0); index.belowThan(indexLimit); index = index.add(1)) {
            initializeEntryAtIndex(table, index);
        }
        return true;
    }

    private static void initializeEntryAtIndex(Pointer table, UnsignedWord index) {
        table.writeByte(indexToTableOffset(index), (byte) UNINITIALIZED_ENTRY);
    }

    private static int getEntryAtIndex(Pointer table, UnsignedWord index) {
        return table.readByte(indexToTableOffset(index));
    }

    /** Set the table entry at a given index. */
    private static void setEntryAtIndex(Pointer table, UnsignedWord index, int value) {
        assert isValidEntry(value) : "Invalid entry";
        assert isUninitializedIndex(table, index) || getEntryAtIndex(table, index) == value : "Overwriting!";
        table.writeByte(indexToTableOffset(index), (byte) value);
    }

    private static boolean isValidEntry(int entry) {
        return ((ENTRY_MIN <= entry) && (entry <= ENTRY_MAX));
    }

    private static boolean isUninitializedEntry(int entry) {
        assert isValidEntry(entry) : "Invalid entry";
        return (entry == UNINITIALIZED_ENTRY);
    }

    private static boolean isMemoryOffsetEntry(int entry) {
        assert isValidEntry(entry) : "Invalid entry";
        return ((MEMORY_OFFSET_MIN <= entry) && (entry <= MEMORY_OFFSET_MAX));
    }

    private static boolean isLinearOffsetEntry(int entry) {
        assert isValidEntry(entry) : "Invalid entry";
        return ((LINEAR_OFFSET_MIN <= entry) && (entry <= LINEAR_OFFSET_MAX));
    }

    private static boolean isExponentialOffsetEntry(int entry) {
        assert isValidEntry(entry) : "Invalid entry";
        int unbiasedEntry = unbiasExponent(entry);
        return ((EXPONENT_MIN <= unbiasedEntry) && (unbiasedEntry <= EXPONENT_MAX));
    }

    private static int biasExponent(int exponent) {
        assert ((EXPONENT_MIN <= exponent) && (exponent <= EXPONENT_MAX)) : "Exponent out of bounds.";
        return exponent + EXPONENT_BIAS;
    }

    private static int unbiasExponent(int entry) {
        int exponent = entry - EXPONENT_BIAS;
        assert ((EXPONENT_MIN <= exponent) && (exponent <= EXPONENT_MAX)) : "Exponent out of bounds.";
        return exponent;
    }

    private static UnsignedWord exponentToOffset(int n) {
        assert ((0 <= n) && (n <= 63)) : "Exponent out of bounds.";
        return WordFactory.unsigned(1L << n);
    }

    private static boolean memoryOffsetStartsCard(UnsignedWord offset) {
        return offset.unsignedRemainder(BYTES_COVERED_BY_ENTRY).equal(0);
    }

    private static boolean memoryOffsetAndLengthCrossesCard(UnsignedWord offset, UnsignedWord length) {
        UnsignedWord startCard = memoryOffsetToIndex(offset);
        UnsignedWord endCard = memoryOffsetToIndex(offset.add(length).subtract(1));
        return startCard.belowThan(endCard);
    }

    private static Pointer getPointerAtOffset(Pointer memory, UnsignedWord currentIndex, int currentEntry) {
        assert isMemoryOffsetEntry(currentEntry) : "Entry out of bounds.";
        UnsignedWord indexOffset = indexToMemoryOffset(currentIndex);
        UnsignedWord entryOffset = entryToMemoryOffset(currentEntry);
        return memory.add(indexOffset).subtract(entryOffset);
    }

    private static UnsignedWord indexToTableOffset(UnsignedWord index) {
        return index.multiply(ENTRY_SIZE_BYTES);
    }

    private static UnsignedWord tableOffsetToIndex(UnsignedWord offset) {
        return offset.unsignedDivide(ENTRY_SIZE_BYTES);
    }

    private static UnsignedWord indexToMemoryOffset(UnsignedWord index) {
        return index.multiply(BYTES_COVERED_BY_ENTRY);
    }

    private static UnsignedWord memoryOffsetToIndex(UnsignedWord offset) {
        return offset.unsignedDivide(BYTES_COVERED_BY_ENTRY);
    }

    private static int memoryOffsetToEntry(UnsignedWord memoryOffset) {
        assert memoryOffset.belowThan(BYTES_COVERED_BY_ENTRY) : "Offset out of bounds.";
        UnsignedWord scaledOffset = memoryOffset.unsignedDivide(memoryOffsetScale());
        assert scaledOffset.multiply(memoryOffsetScale()).equal(memoryOffset) : "Not a multiple.";
        long result = (-scaledOffset.rawValue());
        assert ((MEMORY_OFFSET_MIN <= result) && (result <= MEMORY_OFFSET_MAX)) : "Scaled offset out of bounds.";
        return (int) result;
    }

    /** Turn a table entry into a memory offset. This only handles memory offset entries. */
    private static UnsignedWord entryToMemoryOffset(int entry) {
        assert isMemoryOffsetEntry(entry) : "Entry out of bounds.";
        UnsignedWord entryUnsigned = WordFactory.unsigned(-entry);
        UnsignedWord result = entryUnsigned.multiply(memoryOffsetScale());
        assert (result.belowThan(BYTES_COVERED_BY_ENTRY)) : "Entry out of bounds.";
        return result;
    }

    private static void entryToLog(Log log, int entry) {
        log.signed(entry).string(":");
        if (isUninitializedEntry(entry)) {
            log.string("UninitializedEntry");
        } else if (isMemoryOffsetEntry(entry)) {
            log.string("Memory:").signed(entryToMemoryOffset(entry));
        } else if (isLinearOffsetEntry(entry)) {
            log.string("Linear:").signed(entry);
        } else if (isExponentialOffsetEntry(entry)) {
            log.string("Exponent:").signed(unbiasExponent(entry));
        } else {
            log.string("Unknown");
        }
    }

    public static final class TestingBackDoor {

        private TestingBackDoor() {
        }

        public static void initializeTableToLimitForAsserts(Pointer table, Pointer tableLimit) {
            FirstObjectTable.doInitializeTableToLimit(table, tableLimit);
        }

        public static void initializeTableToIndexForAsserts(Pointer table, UnsignedWord indexLimit) {
            FirstObjectTable.doInitializeTableToLimit(table, indexLimit);
        }

        public static void setTableForObject(Pointer table, Pointer memory, Pointer start, Pointer end) {
            /* Bypass the check for an in-progress VMOperation. */
            FirstObjectTable.setTableForObjectUnchecked(table, memory, start, end);
        }

        public static int getEntryAtIndex(Pointer table, UnsignedWord index) {
            return FirstObjectTable.getEntryAtIndex(table, index);
        }

        public static Pointer getPreciseFirstObjectPointer(Pointer tableStart, Pointer memoryStart, Pointer memoryLimit, UnsignedWord index) {
            return FirstObjectTable.getPreciseFirstObjectPointer(tableStart, memoryStart, memoryLimit, index);
        }

        public static boolean memoryOffsetStartsCard(UnsignedWord offset) {
            return FirstObjectTable.memoryOffsetStartsCard(offset);
        }

        public static boolean memoryOffsetAndLengthCrossesCard(UnsignedWord offset, UnsignedWord length) {
            return FirstObjectTable.memoryOffsetAndLengthCrossesCard(offset, length);
        }

        static void indexToLog(Pointer tableStart, Log log, UnsignedWord index) {
            FirstObjectTable.entryToLog(log, getEntryAtIndex(tableStart, index));
        }

        public static int getMemoryBytesCoveredByEntry() {
            return FirstObjectTable.BYTES_COVERED_BY_ENTRY;
        }

        public static int getMemoryOffsetScale() {
            return FirstObjectTable.memoryOffsetScale();
        }

        public static int getMemoryOffsetMax() {
            return FirstObjectTable.MEMORY_OFFSET_MAX;
        }

        public static int getLinearOffsetMin() {
            return FirstObjectTable.LINEAR_OFFSET_MIN;
        }

        public static int getLinearOffsetMax() {
            return FirstObjectTable.LINEAR_OFFSET_MAX;
        }

        public static int getExponentBias() {
            return FirstObjectTable.EXPONENT_BIAS;
        }

        public static int getUninitializedEntry() {
            return FirstObjectTable.UNINITIALIZED_ENTRY;
        }

        public static UnsignedWord getTableSizeForMemorySize(UnsignedWord memorySize) {
            return FirstObjectTable.getTableSizeForMemorySize(memorySize);
        }

        public static UnsignedWord getTableSizeForMemoryRange(Pointer memoryStart, Pointer memoryLimit) {
            return FirstObjectTable.getTableSizeForMemoryRange(memoryStart, memoryLimit);
        }
    }
}
