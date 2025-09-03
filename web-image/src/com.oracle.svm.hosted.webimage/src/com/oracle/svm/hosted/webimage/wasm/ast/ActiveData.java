/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.ast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.oracle.svm.util.ClassUtil;

/**
 * Manages active data segments of a {@link WasmModule}.
 * <p>
 * Avoids creating data segments for regions filled with NULL bytes.
 */
public class ActiveData {
    public abstract static class Segment implements Comparable<Segment> {
        final long offset;

        Segment(long offset) {
            this.offset = offset;
        }

        public abstract long getSize();

        public long getEnd() {
            return offset + getSize();
        }

        /**
         * Computes whether this segment overlaps with {@code [start, start + size)}.
         */
        boolean overlaps(long start, long size) {
            return start < offset + getSize() && offset < start + size;
        }

        @Override
        public int compareTo(Segment o) {
            return Long.compare(this.offset, o.offset);
        }

        @Override
        public String toString() {
            return ClassUtil.getUnqualifiedName(this.getClass()) + "{offset=" + offset + ", size=" + getSize() + ", [" + offset + ", " + (offset + getSize()) + ")}";
        }

        public long getOffset() {
            return offset;
        }
    }

    public static class DataSegment extends Segment {
        final byte[] data;

        DataSegment(long offset, byte[] data) {
            super(offset);
            assert data.length > 0 : "Empty data segments are not allowed/necessary";
            this.data = data;

            // Any constructed segments shouldn't exceed the NULL threshold
            assert checkNullBytes();
        }

        @Override
        public long getSize() {
            return data.length;
        }

        /**
         * Asserts that the data buffer contains less than {@link #NULL_COUNT_THRESHOLD} consecutive
         * NULL bytes.
         */
        boolean checkNullBytes() {
            int longestStreak = 0;
            for (byte b : data) {
                if (b == 0) {
                    longestStreak++;
                    assert longestStreak < NULL_COUNT_THRESHOLD : longestStreak;
                } else {
                    longestStreak = 0;
                }
            }

            return true;
        }
    }

    public static class NullSegment extends Segment {
        final long size;

        NullSegment(long offset, long size) {
            super(offset);
            assert size > 0 : size;
            this.size = size;
        }

        @Override
        public long getSize() {
            return size;
        }
    }

    /**
     * Threshold after how many NULL bytes a data segment should be split up.
     * <p>
     * Each active data segment has between 4 and 12 bytes overhead in the binary representation:
     * <ol>
     * <li>1 initial byte (determines active/passive, whether an explicit memory index is used)</li>
     * <li>1 byte for the i32.const bytecode indicating the segment offset</li>
     * <li>1-5 bytes encoding the i32 segment offset</li>
     * <li>1-5 bytes encoding the u32 segment size</li>
     * </ol>
     *
     * It also increases the number of data segments whose encoding may increase in size. To account
     * for this, we split up segments if there are 14 consecutive NULL bytes.
     */
    public static final int NULL_COUNT_THRESHOLD = 14;

    /**
     * Set of all segments ordered by base offset.
     */
    protected final TreeSet<Segment> segments = new TreeSet<>();

    public List<Segment> getSegments() {
        return new ArrayList<>(segments);
    }

    /**
     * Whether the interval {@code [offset, offset + size)} intersects any existing segments.
     */
    protected boolean doesIntersect(long offset, long size) {
        return segments.stream().anyMatch(segment -> segment.overlaps(offset, size));
    }

    /**
     * Adds the data at the given offset to the active data set.
     * <p>
     * The data bytes are split up if they contain large contiguous chunks of NULL bytes.
     */
    public void addData(long offset, byte[] data) {
        assert !doesIntersect(offset, data.length) : "Data segment at " + offset + " and size " + data.length + " overlaps with existing segments.";

        ByteArrayOutputStream os = new ByteArrayOutputStream(data.length);
        // Number of trailing NULL bytes in currently read substring
        int numTrailingNulls = 0;
        // Index into data of the start of the current segment
        final AtomicLong segmentStart = new AtomicLong(0);
        boolean isInNullSegment = false;

        Consumer<Segment> addSegment = segment -> {
            segments.add(segment);
            segmentStart.addAndGet(segment.getSize());
        };

        /*
         * Add all accumulated bytes as a data segment.
         */
        Runnable addDataSegment = () -> {
            if (os.size() > 0) {
                addSegment.accept(new DataSegment(offset + segmentStart.get(), os.toByteArray()));
                os.reset();
            }
        };
        IntConsumer addNullSegment = size -> addSegment.accept(new NullSegment(offset + segmentStart.get(), size));

        for (byte b : data) {
            if (b == 0) {
                numTrailingNulls++;

                /*
                 * We reached the threshold, all trailing zeros will be part of a NullSegment
                 */
                if (numTrailingNulls == NULL_COUNT_THRESHOLD) {
                    isInNullSegment = true;
                    addDataSegment.run();
                }
            } else {
                if (isInNullSegment) {
                    /*
                     * We encountered the first non-NULL byte after a NullSegment, add a
                     * NullSegment.
                     */
                    assert os.size() == 0 : "In a NullSegment, output must be empty, was: " + Arrays.toString(os.toByteArray());
                    addNullSegment.accept(numTrailingNulls);
                    isInNullSegment = false;
                } else if (numTrailingNulls > 0) {
                    /*
                     * There were some NULL bytes, but not enough to add a null segment.
                     * Retroactively add them to the output stream.
                     */
                    os.writeBytes(new byte[numTrailingNulls]);
                }

                numTrailingNulls = 0;
                os.write(b);
            }
        }

        if (os.size() > 0) {
            assert !isInNullSegment : "Cannot be in NullSegment if there is output: " + Arrays.toString(os.toByteArray());
            // Add trailing data bytes
            addDataSegment.run();
        }

        assert os.size() == 0 : Arrays.toString(os.toByteArray());

        if (numTrailingNulls > 0) {
            // Add trailing NULL bytes.
            addNullSegment.accept(numTrailingNulls);
        }

        assert segmentStart.get() == data.length : segmentStart.get() + " != " + data.length;
    }

    /**
     * Produces a list of active {@link Data} segments representing all added data and resets the
     * state of this instance.
     * <p>
     * Since WASM memory is zero-initialized, large chunks for NULL bytes can be omitted, so bigger
     * data segments are split into smaller segments to remove unnecessary NULL bytes.
     *
     * @param maxSegments Maximum number of segments this should produce. If this is less than the
     *            current number of data segments, segments will be merged to reduce their number.
     */
    public List<Data> constructDataSegments(int maxSegments) {
        assert maxSegments >= 1 : "Must request at least one data segment, requested " + maxSegments;

        List<DataSegment> dataSegments = segments.stream().filter(DataSegment.class::isInstance).map(DataSegment.class::cast).toList();

        List<Data> list;
        if (dataSegments.size() > maxSegments) {
            list = mergeAllSegments(dataSegments, maxSegments);
        } else {
            list = dataSegments.stream().map(segment -> new Data(null, segment.data.clone(), segment.offset, null)).toList();
        }
        segments.clear();
        return list;
    }

    /**
     * Merges segments in the given {@link DataSegment data segments} and produces exactly
     * {@code maxSegments} {@link Data} instances representing the final list of segments.
     * <p>
     * The segments with the smallest gaps between them are merged to minimize the total size of
     * data segments.
     *
     * @see #findSegmentsToMerge(List, int)
     *
     * @return A list containing exactly {@code maxSegments} instances of {@link Data}
     */
    private static List<Data> mergeAllSegments(List<DataSegment> dataSegments, int maxSegments) {
        BitSet toMerge = findSegmentsToMerge(dataSegments, maxSegments);

        List<Data> list = new ArrayList<>(maxSegments);
        ListIterator<DataSegment> it = dataSegments.listIterator();

        while (it.hasNext()) {
            int idx = it.nextIndex();
            DataSegment thisSegment = it.next();

            /*
             * Search for first index that is no longer marked for merging. All segments in [idx,
             * endIdx] (inclusive) will be merged. This also covers the case where no merging is
             * necessary (idx == endIdx).
             */
            int endIdx = idx;
            while (toMerge.get(endIdx)) {
                assert it.hasNext() : "The last segment was marked to be merged with the next one";
                endIdx = it.nextIndex();
                it.next();
            }

            byte[] data = mergeSegments(dataSegments, idx, endIdx);
            list.add(new Data(null, data, thisSegment.offset, null));
        }

        assert list.size() == maxSegments : list.size() + " != " + maxSegments;

        return list;
    }

    /**
     * Merges all data segments in {@code dataSegments[start, end]} (end is inclusive).
     *
     * @return The contents of the merged data segment. The gaps between the original data segments
     *         are filled with zeroes.
     */
    private static byte[] mergeSegments(List<DataSegment> dataSegments, int start, int end) {
        DataSegment startSegment = dataSegments.get(start);
        int newSize = Math.toIntExact(dataSegments.get(end).getEnd() - startSegment.offset);
        byte[] data = Arrays.copyOf(startSegment.data, newSize);
        for (int i = start + 1; i <= end; i++) {
            DataSegment s = dataSegments.get(i);
            System.arraycopy(s.data, 0, data, Math.toIntExact(s.offset - startSegment.offset), Math.toIntExact(s.getSize()));
        }
        return data;
    }

    /**
     * Determines the optimal set of segments to merge.
     * <p>
     * The segments with the smallest gaps between them are marked for merging to reduce the
     * overhead of the additional NULL-bytes.
     * <p>
     * Constructs a {@link PriorityQueue} of all gap sizes, then pops the top {@code k} entries
     * (where {@code k} is the number of necessary merges), and uses them to set bits in a
     * {@link BitSet}.
     * <p>
     * For {@code n} being the total number of segments, the time complexities are:
     * <ul>
     * <li>Populating the {@link PriorityQueue}: {@code O(n * log(n)}</li>
     * <li>Creating the {@link BitSet}: {@code O(n)}</li>
     * <li>Filling the {@link BitSet}: {@code O(k * log(n))}</li>
     * </ul>
     *
     * Total time complexity is {@code O(n * log(n)} because {@code k < n}.
     *
     * @param maxSegments Maximum number of segments that should exist after merging. Must be
     *            smaller than the number of data segments.
     * @return A bitset. If the bit at index {@code i} is set, then the segments at index {@code i}
     *         and {@code i + 1} should be merged.
     */
    private static BitSet findSegmentsToMerge(List<DataSegment> dataSegments, int maxSegments) {
        int numMerges = dataSegments.size() - maxSegments;
        assert numMerges > 0 : "No merges required. Only call this method if merges are required.";

        PriorityQueue<GapEntry> gaps = new PriorityQueue<>(dataSegments.size() - 1);

        for (int i = 0; i < dataSegments.size() - 1; i++) {
            gaps.add(new GapEntry(dataSegments.get(i + 1).offset - dataSegments.get(i + 1).getEnd(), i));
        }

        BitSet toMerge = new BitSet(dataSegments.size());

        for (int i = 0; i < numMerges; i++) {
            GapEntry gap = gaps.poll();
            assert gap != null;
            toMerge.set(gap.leftSegmentIdx);
        }
        return toMerge;
    }

    /**
     * @param gap Number of bytes between the two segments
     * @param leftSegmentIdx The segment index of the segment that comes immediately before the gap.
     */
    private record GapEntry(long gap, int leftSegmentIdx) implements Comparable<GapEntry> {

        /**
         * Compares on the gap size then of the {@code leftSegmentIdx}. This way, no two elements
         * compare equal and there is no ambiguity in the priority queue.
         */
        @Override
        public int compareTo(GapEntry o) {
            if (gap == o.gap) {
                return Integer.compare(leftSegmentIdx, o.leftSegmentIdx);
            } else {
                return Long.compare(gap, o.gap);
            }
        }
    }
}
