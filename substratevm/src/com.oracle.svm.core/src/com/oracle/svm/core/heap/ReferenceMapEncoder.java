/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core.heap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;

import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.ByteArrayReader;

public class ReferenceMapEncoder {

    public interface OffsetIterator extends PrimitiveIterator.OfInt {
        @Override
        boolean hasNext();

        @Override
        int nextInt();

        /**
         * Returns whether the next offset that will be returned by {@link #nextInt()} refers to a
         * compressed pointer.
         * 
         * @throws NoSuchElementException
         */
        boolean isNextCompressed();
    }

    public interface Input {
        boolean isEmpty();

        OffsetIterator getOffsets();
    }

    private final HashMap<Input, Long> usageCounts = new HashMap<>();
    private final HashMap<Input, Long> encodings = new HashMap<>();
    private final UnsafeArrayTypeWriter writeBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());

    public void add(Input input) {
        if (input == null || input.isEmpty()) {
            return;
        }
        Long oldCount = usageCounts.get(input);
        Long newCount = oldCount == null ? 1 : oldCount.longValue() + 1;
        usageCounts.put(input, newCount);
    }

    public byte[] encodeAll(PinnedAllocator allocator) {
        assert writeBuffer.getBytesWritten() == 0 : "encodeAll() must not be called multiple times";

        /*
         * The table always starts with the empty reference map. This allows clients to actually
         * iterate the empty reference map, making a check for the empty map optional.
         */
        assert CodeInfoQueryResult.EMPTY_REFERENCE_MAP == writeBuffer.getBytesWritten();
        writeBuffer.putU1(ReferenceMapDecoder.GAP_END_OF_TABLE);

        /*
         * Sort reference map by usage count, so that frequently used maps get smaller indices
         * (which can be encoded in fewer bytes).
         */
        List<Map.Entry<Input, Long>> sortedEntries = new ArrayList<>(usageCounts.entrySet());
        sortedEntries.sort((o1, o2) -> -Long.compare(o1.getValue(), o2.getValue()));

        for (Map.Entry<Input, Long> entry : sortedEntries) {
            Input map = entry.getKey();
            encodings.put(map, encode(map.getOffsets()));
        }

        int length = TypeConversion.asS4(writeBuffer.getBytesWritten());
        return writeBuffer.toArray(newByteArray(allocator, length));
    }

    private static byte[] newByteArray(PinnedAllocator allocator, int length) {
        return allocator == null ? new byte[length] : (byte[]) allocator.newArray(byte.class, length);
    }

    public long lookupEncoding(Input referenceMap) {
        if (referenceMap == null) {
            return CodeInfoQueryResult.NO_REFERENCE_MAP;
        } else if (referenceMap.isEmpty()) {
            return CodeInfoQueryResult.EMPTY_REFERENCE_MAP;
        } else {
            Long result = encodings.get(referenceMap);
            assert result != null && result.longValue() != CodeInfoQueryResult.NO_REFERENCE_MAP && result.longValue() != CodeInfoQueryResult.EMPTY_REFERENCE_MAP;
            return result.longValue();
        }
    }

    /**
     * Build a byte array that encodes list of offsets. The encoding is a run-length-encoding of the
     * gaps and elements.
     *
     * @param offsets A strictly increasing supply (with -1 as the marker end value) of positive
     *            0-origin offsets. For example, if a stack frame is laid out
     *
     *            <pre>
     * Word offset | Type
     * ------------+----------
     *           0 | primitive
     *           1 | primitive
     *           2 | Pointer
     *           3 | Pointer
     *           4 | Pointer
     *           5 | primitive
     *            </pre>
     *
     *            to record the Pointers within that frame I want to encode the offsets 2, 3, and 4.
     *            There is an initial run of 2 adjacent primitives followed by 3 adjacent pointers,
     *            so the encoding is
     *
     *            <pre>
     * Byte offset | Value | Meaning
     * ------------+-------+--------
     *           0 | 2     | A run of 2 primitives
     *           1 | 3     | A run of 3 Pointers
     *            </pre>
     *
     *            (The trailing primitive is not encoded, because this is an encoding of the given
     *            offsets.)
     *            <p>
     *            A trick of the encoding is that if a number, N, greater than or equal to 127 has
     *            to be represented, it is encoded by a triple of bytes <code>127 0 (N-127)</code>
     *            so that the decoder doesn't have to know about that trick, because it just
     *            processes, for example, a run of 127 primitives, 0 Pointers, and then (N-127)
     *            primitives. If (N-127) is greater than or equal to 127, the encoding trick is
     *            repeated as necessary.
     *
     * @return The index into the final bytes.
     */
    private long encode(OffsetIterator offsets) {
        int refMapSlotSize = SubstrateReferenceMap.getSlotSizeInBytes();
        int compressedSlots = ConfigurationValues.getObjectLayout().getCompressedReferenceSize() / refMapSlotSize;
        int uncompressedSlots = ConfigurationValues.getObjectLayout().getReferenceSize() / refMapSlotSize;

        long startIndex = writeBuffer.getBytesWritten();
        int run = 0;
        int gap = 0;

        boolean expectedCompressed = false;
        int expectedOffset = 0;
        while (offsets.hasNext()) {
            boolean compressed = offsets.isNextCompressed();
            int offset = offsets.nextInt();
            if (offset == expectedOffset && compressed == expectedCompressed) {
                // An adjacent offset in this run.
                run += 1;
            } else {
                assert offset >= expectedOffset : "values must be strictly increasing";
                if (run > 0) {
                    // The end of a run. Encode the *previous* gap and this run of offsets.
                    encodeGap(gap);
                    encodePointers(run, expectedCompressed);
                }
                // Beginning of the next gap+run pair.
                gap = offset - expectedOffset;
                run = 1;
            }
            int slots = (compressed ? compressedSlots : uncompressedSlots);
            expectedOffset = offset + slots;
            expectedCompressed = compressed;
        }
        if (run > 0) {
            encodeGap(gap);
            encodePointers(run, expectedCompressed);
        }

        /* End marker so that iterator later knows where to stop. */
        writeBuffer.putU1(ReferenceMapDecoder.GAP_END_OF_TABLE);

        return startIndex;
    }

    /**
     * Encode a gap size. If the value fits in one byte, then it is encoded as one byte. If the
     * value is larger than what will fit in one byte, then the value is encoded as the triple:
     * <code>MAX 0 encode(value-MAX)</code>. This works because the decoder will process this as a
     * gap of size MAX followed by zero pointers and then another gap of size (value-MAX).
     */
    private void encodeGap(int size) {
        final int maxValue = ReferenceMapDecoder.GAP_END_OF_TABLE - 1;

        int residue = size;
        // Peel off instances of the maximum value.
        while (residue > maxValue) {
            writeBuffer.putU1(maxValue);
            writeBuffer.putS1(0); // pointers
            residue -= maxValue;
        }
        writeBuffer.putU1(residue);
    }

    /**
     * Encode a number of pointers. Compressed pointers are encoded as a negative value. If the
     * count fits in one signed byte, then it is encoded as one byte. If the value is larger than
     * what will fit in one byte, then the value is encoded as the triple:
     * <code>MAX 0 encode(value-MAX)</code>. This works because the decoder will process this as MAX
     * number of pointers, then a gap of zero, and then another number of (value-MAX) pointers.
     */
    private void encodePointers(int count, boolean compressed) {
        int residual = compressed ? -count : count;
        if (residual < 0) {
            while (residual < Byte.MIN_VALUE) {
                writeBuffer.putS1(Byte.MIN_VALUE);
                writeBuffer.putU1(0); // gap
                residual -= Byte.MIN_VALUE;
            }
        } else {
            while (residual > Byte.MAX_VALUE) {
                writeBuffer.putS1(Byte.MAX_VALUE);
                writeBuffer.putU1(0); // gap
                residual -= Byte.MAX_VALUE;
            }
        }
        assert residual >= Byte.MIN_VALUE && residual <= Byte.MAX_VALUE;
        writeBuffer.putS1(residual);
    }
}
