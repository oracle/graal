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
package com.oracle.svm.core.genscavenge.remset;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.word.Word;

/**
 * A "first object table" to tell me the start of the first object that crosses onto a card
 * remembered set memory region.
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
     * The number of bytes of memory covered by an entry. Since the indexes into the CardTable are
     * used to index into the FirstObjectTable, these need to have the same value.
     */
    private static final int BYTES_COVERED_BY_ENTRY = CardTable.BYTES_COVERED_BY_ENTRY;

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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initializeTable(Pointer table, UnsignedWord size) {
        if (SubstrateUtil.HOSTED) {
            // Initialize this table unconditionally as this simplifies a few things.
            doInitializeTable(table, size);
        } else {
            // There is no need to initialize this table unless assertions are enabled.
            assert doInitializeTable(table, size);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean doInitializeTable(Pointer table, UnsignedWord size) {
        UnmanagedMemoryUtil.fill(table, size, (byte) UNINITIALIZED_ENTRY);
        return true;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setTableForObject(Pointer table, UnsignedWord startOffset, UnsignedWord endOffset) {
        assert startOffset.belowThan(endOffset);
        UnsignedWord startIndex = memoryOffsetToIndex(startOffset);
        UnsignedWord endIndex = memoryOffsetToIndex(endOffset.subtract(1));
        boolean startsAtCardBoundary = startOffset.unsignedRemainder(BYTES_COVERED_BY_ENTRY).equal(0);
        if (startIndex.equal(endIndex) && !startsAtCardBoundary) {
            // The object neither crosses nor starts on a card boundary
            return;
        }

        // Write the first entry.
        if (startsAtCardBoundary) {
            setEntryAtIndex(table, startIndex, 0);
        } else {
            startIndex = startIndex.add(1);
            UnsignedWord memoryIndexOffset = indexToMemoryOffset(startIndex);
            int entry = memoryOffsetToEntry(memoryIndexOffset.subtract(startOffset));
            setEntryAtIndex(table, startIndex, entry);
        }

        // Write subsequent entries.
        // First, as many linear offsets as needed.
        UnsignedWord linearIndexMax = UnsignedUtils.min(endIndex, startIndex.add(LINEAR_OFFSET_MAX));
        UnsignedWord entryIndex = startIndex.add(1);
        int entry = LINEAR_OFFSET_MIN;
        while (entryIndex.belowOrEqual(linearIndexMax)) {
            setEntryAtIndex(table, entryIndex, entry);
            entryIndex = entryIndex.add(1);
            entry++;
        }

        // Next, as many exponential offsets as needed.
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

    /**
     * In case of imprecise card marking, we mark the card table at the address of the object (and
     * not at the address of the reference). So, we must skip over the first object if it starts
     * outside the current card.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static Pointer getFirstObjectImprecise(Pointer tableStart, Pointer objectsStart, UnsignedWord index) {
        Pointer result;
        Pointer firstObject = getFirstObject(tableStart, objectsStart, index);
        Pointer indexedMemoryStart = objectsStart.add(indexToMemoryOffset(index));
        // If the object starts before the memory for this index, skip over it.
        if (firstObject.belowThan(indexedMemoryStart)) {
            Object crossingObject = firstObject.toObjectNonNull();
            result = LayoutEncoding.getObjectEndInGC(crossingObject);
        } else {
            assert firstObject.equal(indexedMemoryStart) : "preciseFirstPointer.equal(indexedMemoryStart)";
            result = indexedMemoryStart;
        }
        assert objectsStart.belowOrEqual(result) : "memoryStart.belowOrEqual(result)";
        return result;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer getFirstObject(Pointer tableStart, Pointer objectsStart, UnsignedWord index) {
        UnsignedWord currentIndex = index;
        int currentEntry = getEntryAtIndex(tableStart, currentIndex);
        assert currentEntry != UNINITIALIZED_ENTRY : "uninitialized first object table entry";

        if (currentEntry > MEMORY_OFFSET_MAX) {
            // Process all exponential entries.
            while (currentEntry > LINEAR_OFFSET_MAX) {
                int exponent = unbiasExponent(currentEntry);
                UnsignedWord deltaIndex = exponentToOffset(exponent);
                assert deltaIndex.belowOrEqual(currentIndex) : "Delta out of bounds.";
                currentIndex = currentIndex.subtract(deltaIndex);
                currentEntry = getEntryAtIndex(tableStart, currentIndex);
            }

            // Process all linear entries
            if (currentEntry > MEMORY_OFFSET_MAX) {
                currentIndex = currentIndex.subtract(currentEntry);
                currentEntry = getEntryAtIndex(tableStart, currentIndex);
            }
        }

        // The current entry is now a memory offset.
        UnsignedWord memoryOffset = entryToMemoryOffset(currentIndex, currentEntry);
        Pointer result = objectsStart.add(memoryOffset);
        assert objectsStart.belowOrEqual(result) : "chunkStart.belowOrEqual(result)";
        return result;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord entryToMemoryOffset(UnsignedWord index, int entry) {
        assert isMemoryOffsetEntry(entry) : "Entry out of bounds.";
        UnsignedWord entryOffset = Word.unsigned(-entry).multiply(memoryOffsetScale());
        assert entryOffset.belowThan(BYTES_COVERED_BY_ENTRY) : "Entry out of bounds.";

        UnsignedWord indexOffset = indexToMemoryOffset(index);
        return indexOffset.subtract(entryOffset);
    }

    static boolean verify(Pointer tableStart, Pointer objectsStart, Pointer objectsLimit) {
        UnsignedWord indexLimit = getTableSizeForMemoryRange(objectsStart, objectsLimit);
        for (UnsignedWord index = Word.unsigned(0); index.belowThan(indexLimit); index = index.add(1)) {
            Pointer objStart = getFirstObject(tableStart, objectsStart, index);
            if (objStart.belowThan(objectsStart) || objectsLimit.belowOrEqual(objStart)) {
                Log.log().string("The first object table entry at index ").unsigned(index).string(" points to an object that is outside of the current chunk:  obj: ").zhex(objStart)
                                .string(", chunk: ")
                                .zhex(objectsStart).string(" - ").zhex(objectsLimit).newline();
                return false;
            }

            Pointer entryStart = objectsStart.add(indexToMemoryOffset(index));
            if (!objStart.belowOrEqual(entryStart)) {
                Log.log().string("The first object table entry at index ").unsigned(index).string(" points to an object is not crossing nor starting at a card boundary:  obj: ").zhex(objStart)
                                .string(", chunk: ").zhex(objectsStart).string(" - ").zhex(objectsLimit).newline();
                return false;
            }

            Object obj = objStart.toObject();
            Pointer objEnd = LayoutEncoding.getObjectEndInGC(obj);
            if (!entryStart.belowThan(objEnd)) {
                Log.log().string("The first object table entry at index ").unsigned(index).string(" points to an object is not crossing nor starting at a card boundary:  obj: ").zhex(objStart)
                                .string(" - ").zhex(objEnd).string(", chunk: ").zhex(objectsStart).string(" - ").zhex(objectsLimit).newline();
                return false;
            }
        }
        return true;
    }

    private static UnsignedWord getTableSizeForMemoryRange(Pointer memoryStart, Pointer memoryLimit) {
        assert memoryStart.belowOrEqual(memoryLimit) : "Pointers out of order";
        UnsignedWord memorySize = memoryLimit.subtract(memoryStart);
        UnsignedWord roundedMemory = UnsignedUtils.roundUp(memorySize, Word.unsigned(BYTES_COVERED_BY_ENTRY));
        UnsignedWord index = FirstObjectTable.memoryOffsetToIndex(roundedMemory);
        return index.multiply(ENTRY_SIZE_BYTES);
    }

    /**
     * The multiplier from memory offsets to byte offsets into the previous card. This is the
     * granularity to which I can point to the start of an object.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int memoryOffsetScale() {
        return ConfigurationValues.getObjectLayout().getAlignment();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getEntryAtIndex(Pointer table, UnsignedWord index) {
        return table.readByte(indexToTableOffset(index));
    }

    /** Set the table entry at a given index. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void setEntryAtIndex(Pointer table, UnsignedWord index, int value) {
        assert isValidEntry(value) : "Invalid entry";
        assert isUninitializedIndex(table, index) || getEntryAtIndex(table, index) == value : "Overwriting!";
        table.writeByte(indexToTableOffset(index), (byte) value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isUninitializedIndex(Pointer table, UnsignedWord index) {
        int entry = getEntryAtIndex(table, index);
        return isUninitializedEntry(entry);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isValidEntry(int entry) {
        return ENTRY_MIN <= entry && entry <= ENTRY_MAX;
    }

    /** May only be used for assertions. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isUninitializedEntry(int entry) {
        assert isValidEntry(entry) : "Invalid entry";
        return entry == UNINITIALIZED_ENTRY;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isMemoryOffsetEntry(int entry) {
        assert isValidEntry(entry) : "Invalid entry";
        return MEMORY_OFFSET_MIN <= entry && entry <= MEMORY_OFFSET_MAX;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int biasExponent(int exponent) {
        assert EXPONENT_MIN <= exponent && exponent <= EXPONENT_MAX : "Exponent out of bounds.";
        return exponent + EXPONENT_BIAS;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int unbiasExponent(int entry) {
        int exponent = entry - EXPONENT_BIAS;
        assert EXPONENT_MIN <= exponent && exponent <= EXPONENT_MAX : "Exponent out of bounds.";
        return exponent;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord exponentToOffset(int n) {
        assert 0 <= n && n <= 63 : "Exponent out of bounds.";
        return Word.unsigned(1L << n);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord indexToTableOffset(UnsignedWord index) {
        return index.multiply(ENTRY_SIZE_BYTES);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord indexToMemoryOffset(UnsignedWord index) {
        return index.multiply(BYTES_COVERED_BY_ENTRY);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord memoryOffsetToIndex(UnsignedWord offset) {
        return offset.unsignedDivide(BYTES_COVERED_BY_ENTRY);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int memoryOffsetToEntry(UnsignedWord memoryOffset) {
        assert memoryOffset.belowThan(BYTES_COVERED_BY_ENTRY) : "Offset out of bounds.";
        UnsignedWord scaledOffset = memoryOffset.unsignedDivide(memoryOffsetScale());
        assert scaledOffset.multiply(memoryOffsetScale()).equal(memoryOffset) : "Not a multiple.";
        long result = (-scaledOffset.rawValue());
        assert MEMORY_OFFSET_MIN <= result && result <= MEMORY_OFFSET_MAX : "Scaled offset out of bounds.";
        return (int) result;
    }
}
