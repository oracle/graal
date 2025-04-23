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

package com.oracle.svm.hosted.webimage.test.unit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.oracle.svm.hosted.webimage.wasm.ast.ActiveData;
import com.oracle.svm.hosted.webimage.wasm.ast.Data;

public class WasmActiveDataTest {

    /**
     * Parameter for {@link ActiveData#constructDataSegments(int)}.
     * <p>
     * Should be high enough to not require any merges for regular tests (tests that explicitly test
     * the merge functionality specify their own value).
     */
    private static final int MAX_SEGMENTS = Integer.MAX_VALUE;

    ActiveData activeData;

    @Before
    public void setUp() {
        activeData = new ActiveData();
    }

    /**
     * Checks that the given {@link ActiveData} spans a contiguous memory region
     * {@code [start, start + size)}.
     */
    public static void checkContiguous(ActiveData activeData, long start, long size) {
        List<ActiveData.Segment> segments = activeData.getSegments();

        long expectedStart = start;

        for (ActiveData.Segment segment : segments) {
            assertEquals("Start of " + segment, expectedStart, segment.getOffset());
            expectedStart += segment.getSize();
        }

        assertEquals(size, expectedStart - start);
    }

    @Test
    public void noNullBytes() {
        long offset = 1234;
        byte[] bytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        activeData.addData(offset, bytes);

        checkContiguous(activeData, offset, bytes.length);
        List<Data> segments = activeData.constructDataSegments(MAX_SEGMENTS);

        assertEquals(1, segments.size());
        Data data = segments.get(0);

        assertTrue(data.active);
        assertEquals(offset, data.offset);
        assertArrayEquals(bytes, data.data);
    }

    @Test
    public void onlyNullBytes() {
        long offset = 6789;
        byte[] bytes = new byte[1 << 16];
        activeData.addData(offset, bytes);

        checkContiguous(activeData, offset, bytes.length);
        List<Data> segments = activeData.constructDataSegments(MAX_SEGMENTS);

        assertTrue(segments.isEmpty());
    }

    @Test
    public void splittingSimple() {
        long offset = 6789;
        byte[] bytes = new byte[1 << 16];
        int dataOffset = 123;
        byte dataByte = 1;
        byte[] dataBytes = new byte[100];
        Arrays.fill(dataBytes, dataByte);
        System.arraycopy(dataBytes, 0, bytes, dataOffset, dataBytes.length);

        activeData.addData(offset, bytes);

        checkContiguous(activeData, offset, bytes.length);
        List<Data> segments = activeData.constructDataSegments(MAX_SEGMENTS);

        assertEquals(1, segments.size());
        Data data = segments.get(0);

        assertTrue(data.active);
        assertEquals(offset + dataOffset, data.offset);
        assertArrayEquals(dataBytes, data.data);
    }

    /**
     * Tests merging of segments if max number of segments is reached.
     * <p>
     * The builder is asked to produce at most two data segments from the following data:
     *
     * <pre>
     *     [12, <99 x 0>, 13, <99 x 0>, 14, <899 x 0>, 15, ...]
     * </pre>
     *
     * This would usually result in four data segments with one byte each. But if the max segment
     * limitation requires two merges. The first two gaps are smaller, so the first three non-zero
     * element should be merged resulting in:
     *
     * <pre>
     *     offset    0: [12, <99 x 0>, 13, <99 x 0>, 13]
     *     offset 1000: [14]
     * </pre>
     */
    @Test
    public void maxSegmentNumberSimple() {
        ActiveData data = new ActiveData();
        byte[] bytes = new byte[1 << 16];
        int offset1 = 0;
        int offset2 = 100;
        int offset3 = 200;
        int offset4 = 1000;

        bytes[offset1] = 12;
        bytes[offset2] = 13;
        bytes[offset3] = 14;
        bytes[offset4] = 15;

        data.addData(0, bytes);

        checkContiguous(data, 0, bytes.length);
        List<Data> segments = data.constructDataSegments(2);

        assertEquals(2, segments.size());

        Data segment1 = segments.getFirst();
        assertEquals(0, segment1.offset);
        assertEquals(201, segment1.getSize());
        assertArrayEquals(Arrays.copyOf(segment1.data, 201), segment1.data);
        Data segment2 = segments.getLast();
        assertEquals(1000, segment2.offset);
        assertEquals(1, segment2.getSize());
        assertEquals(bytes[offset4], segment2.data[0]);
    }
}
