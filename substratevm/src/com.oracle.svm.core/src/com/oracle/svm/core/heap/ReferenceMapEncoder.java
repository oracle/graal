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
package com.oracle.svm.core.heap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.TypeWriter;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;

import com.oracle.svm.core.amd64.FrameAccess;
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
        encodeEndOfTable();

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
     * Build a byte array that encodes the passed list of offsets. The encoding is a run-length
     * encoding of runs of compressed or of uncompressed references, with
     * {@linkplain TypeWriter#putUV(long) variable-length encoding for the lengths}.
     *
     * @return The index into the final bytes.
     */
    private long encode(OffsetIterator offsets) {
        int compressedSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        int uncompressedSize = FrameAccess.uncompressedReferenceSize();

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
                    encodeRun(gap, run, expectedCompressed);
                }
                // Beginning of the next gap+run pair.
                gap = offset - expectedOffset;
                run = 1;
            }
            int size = (compressed ? compressedSize : uncompressedSize);
            expectedOffset = offset + size;
            expectedCompressed = compressed;
        }
        if (run > 0) {
            encodeRun(gap, run, expectedCompressed);
        }
        encodeEndOfTable();
        return startIndex;
    }

    private void encodeRun(int gap, int refsCount, boolean compressed) {
        assert gap >= 0 && refsCount >= 0;
        writeBuffer.putUV(gap);
        writeBuffer.putSV(compressed ? -refsCount : refsCount);
    }

    private void encodeEndOfTable() {
        encodeRun(0, 0, false);
    }
}
