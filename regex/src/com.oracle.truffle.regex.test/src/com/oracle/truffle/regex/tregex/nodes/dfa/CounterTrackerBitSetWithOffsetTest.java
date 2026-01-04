/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.regex.tregex.automaton.TransitionOp;

public class CounterTrackerBitSetWithOffsetTest {

    private CounterTrackerBitSetWithOffset bitSet;
    private long[] fixedData;

    @Before
    public void setUp() {
        reset();
    }

    private void reset() {
        CounterTrackerData.Builder builder = new CounterTrackerData.Builder();
        bitSet = new CounterTrackerBitSetWithOffset(100, 200, 3, builder);
        fixedData = new long[builder.getFixedDataSize()];
        bitSet.init(fixedData, null);
    }

    @Test
    public void testOffsetGtMax() {
        bitSet.set1(0, TransitionOp.union, fixedData);
        for (int i = 0; i < 63; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
        }
        Assert.assertArrayEquals(new int[]{64}, bitSet.getValues(fixedData, 0));
        bitSet.set1(0, TransitionOp.union, fixedData);
        Assert.assertArrayEquals(new int[]{64, 1}, bitSet.getValues(fixedData, 0));
        for (int i = 0; i < 35; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
            Assert.assertFalse(bitSet.anyGeMin(0, fixedData, null));
        }
        Assert.assertArrayEquals(new int[]{99, 36}, bitSet.getValues(fixedData, 0));
        for (int i = 0; i < 164; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
            Assert.assertTrue(bitSet.anyGeMin(0, fixedData, null));
        }
        Assert.assertArrayEquals(new int[]{200}, bitSet.getValues(fixedData, 0));
        for (int i = 0; i < 64; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
            Assert.assertFalse(bitSet.anyGeMin(0, fixedData, null));
        }
    }

    @Test
    public void testOffsetGtMaxUnion() {
        bitSet.set1(0, TransitionOp.union, fixedData);
        bitSet.set1(1, TransitionOp.union, fixedData);
        bitSet.set1(2, TransitionOp.union, fixedData);
        for (int i = 0; i < 62; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
        }
        Assert.assertArrayEquals(new int[]{63}, bitSet.getValues(fixedData, 0));
        bitSet.inc(0, 1, TransitionOp.union, fixedData);
        Assert.assertArrayEquals(new int[]{63}, bitSet.getValues(fixedData, 0));
        Assert.assertArrayEquals(new int[]{64, 1}, bitSet.getValues(fixedData, 1));
        bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
        Assert.assertArrayEquals(new int[]{64}, bitSet.getValues(fixedData, 0));
        bitSet.inc(0, 2, TransitionOp.union, fixedData);
        Assert.assertArrayEquals(new int[]{64}, bitSet.getValues(fixedData, 0));
        Assert.assertArrayEquals(new int[]{65, 1}, bitSet.getValues(fixedData, 2));
        bitSet.inc(2, 0, TransitionOp.union, fixedData);
        Assert.assertArrayEquals(new int[]{66, 64, 2}, bitSet.getValues(fixedData, 0));
    }

    @Test
    public void testOffsetGtMax2() {
        bitSet.set1(0, TransitionOp.union, fixedData);
        for (int i = 0; i < 64; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
        }
        Assert.assertArrayEquals(new int[]{65}, bitSet.getValues(fixedData, 0));
        bitSet.set1(0, TransitionOp.union, fixedData);
        Assert.assertArrayEquals(new int[]{65, 1}, bitSet.getValues(fixedData, 0));
        for (int i = 0; i < 34; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
            Assert.assertFalse(bitSet.anyGeMin(0, fixedData, null));
        }
        Assert.assertArrayEquals(new int[]{99, 35}, bitSet.getValues(fixedData, 0));
        for (int i = 0; i < 165; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
            Assert.assertTrue(bitSet.anyGeMin(0, fixedData, null));
        }
        Assert.assertArrayEquals(new int[]{200}, bitSet.getValues(fixedData, 0));
        for (int i = 0; i < 64; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
            Assert.assertFalse(bitSet.anyGeMin(0, fixedData, null));
        }
    }

    @Test
    public void testOffsetGtMax3() {
        bitSet.set1(0, TransitionOp.union, fixedData);
        for (int i = 0; i < 128; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
        }
        Assert.assertArrayEquals(new int[]{129}, bitSet.getValues(fixedData, 0));
        bitSet.set1(0, TransitionOp.union, fixedData);
        Assert.assertArrayEquals(new int[]{129, 1}, bitSet.getValues(fixedData, 0));
        for (int i = 0; i < 71; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
            Assert.assertTrue(bitSet.anyGeMin(0, fixedData, null));
        }
        Assert.assertArrayEquals(new int[]{200, 72}, bitSet.getValues(fixedData, 0));
        for (int i = 0; i < 27; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
            Assert.assertFalse(bitSet.anyGeMin(0, fixedData, null));
        }
        Assert.assertArrayEquals(new int[]{99}, bitSet.getValues(fixedData, 0));
        for (int i = 0; i < 101; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
            Assert.assertTrue(bitSet.anyGeMin(0, fixedData, null));
        }
        Assert.assertArrayEquals(new int[]{200}, bitSet.getValues(fixedData, 0));
        for (int i = 0; i < 64; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
            Assert.assertFalse(bitSet.anyGeMin(0, fixedData, null));
        }
    }

    @Test
    public void testBasicOps() {
        Assert.assertArrayEquals(new int[0], bitSet.getValues(fixedData, 0));
        Assert.assertArrayEquals(new int[0], bitSet.getValues(fixedData, 1));
        bitSet.set1(0, TransitionOp.union, fixedData);
        Assert.assertArrayEquals(new int[]{1}, bitSet.getValues(fixedData, 0));
        bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
        Assert.assertArrayEquals(new int[]{2}, bitSet.getValues(fixedData, 0));
        bitSet.inc(0, 1, TransitionOp.overwrite, fixedData);
        Assert.assertArrayEquals(new int[]{2}, bitSet.getValues(fixedData, 0));
        Assert.assertArrayEquals(new int[]{3}, bitSet.getValues(fixedData, 1));
        bitSet.set1(1, TransitionOp.union, fixedData);
        Assert.assertArrayEquals(new int[]{2}, bitSet.getValues(fixedData, 0));
        Assert.assertArrayEquals(new int[]{3, 1}, bitSet.getValues(fixedData, 1));
        bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        Assert.assertArrayEquals(new int[]{4, 2}, bitSet.getValues(fixedData, 1));
        bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        Assert.assertArrayEquals(new int[]{2}, bitSet.getValues(fixedData, 0));
        Assert.assertArrayEquals(new int[]{5, 3}, bitSet.getValues(fixedData, 1));
        bitSet.maintain(1, 0, TransitionOp.union, fixedData);
        Assert.assertArrayEquals(new int[]{5, 3, 2}, bitSet.getValues(fixedData, 0));
        Assert.assertArrayEquals(new int[]{5, 3}, bitSet.getValues(fixedData, 1));
        for (int i = 0; i < 80; i++) {
            bitSet.inc(0, 0, TransitionOp.overwrite, fixedData);
        }
        Assert.assertArrayEquals(new int[]{85, 83, 82}, bitSet.getValues(fixedData, 0));
        bitSet.maintain(0, 1, TransitionOp.union, fixedData);
        Assert.assertArrayEquals(new int[]{85, 83, 82, 5, 3}, bitSet.getValues(fixedData, 1));
        Assert.assertTrue(bitSet.anyLtMin(1, fixedData, null));
        Assert.assertTrue(bitSet.anyLtMax(1, fixedData, null));
        Assert.assertFalse(bitSet.anyGeMin(1, fixedData, null));
        for (int i = 0; i < 14; i++) {
            bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        }
        Assert.assertArrayEquals(new int[]{99, 97, 96, 19, 17}, bitSet.getValues(fixedData, 1));
        Assert.assertTrue(bitSet.anyLtMin(1, fixedData, null));
        Assert.assertTrue(bitSet.anyLtMax(1, fixedData, null));
        Assert.assertFalse(bitSet.anyGeMin(1, fixedData, null));
        bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        Assert.assertArrayEquals(new int[]{100, 98, 97, 20, 18}, bitSet.getValues(fixedData, 1));
        Assert.assertTrue(bitSet.anyLtMin(1, fixedData, null));
        Assert.assertTrue(bitSet.anyLtMax(1, fixedData, null));
        Assert.assertTrue(bitSet.anyGeMin(1, fixedData, null));
        for (int i = 0; i < 80; i++) {
            bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        }
        Assert.assertArrayEquals(new int[]{180, 178, 177, 100, 98}, bitSet.getValues(fixedData, 1));
        Assert.assertTrue(bitSet.anyLtMin(1, fixedData, null));
        Assert.assertTrue(bitSet.anyLtMax(1, fixedData, null));
        Assert.assertTrue(bitSet.anyGeMin(1, fixedData, null));
        bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        Assert.assertArrayEquals(new int[]{181, 179, 178, 101, 99}, bitSet.getValues(fixedData, 1));
        Assert.assertTrue(bitSet.anyLtMin(1, fixedData, null));
        Assert.assertTrue(bitSet.anyLtMax(1, fixedData, null));
        Assert.assertTrue(bitSet.anyGeMin(1, fixedData, null));
        bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        Assert.assertArrayEquals(new int[]{182, 180, 179, 102, 100}, bitSet.getValues(fixedData, 1));
        Assert.assertFalse(bitSet.anyLtMin(1, fixedData, null));
        Assert.assertTrue(bitSet.anyLtMax(1, fixedData, null));
        Assert.assertTrue(bitSet.anyGeMin(1, fixedData, null));
        bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        Assert.assertArrayEquals(new int[]{183, 181, 180, 103, 101}, bitSet.getValues(fixedData, 1));
        Assert.assertFalse(bitSet.anyLtMin(1, fixedData, null));
        Assert.assertTrue(bitSet.anyLtMax(1, fixedData, null));
        Assert.assertTrue(bitSet.anyGeMin(1, fixedData, null));
        for (int i = 0; i < 98; i++) {
            bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        }
        Assert.assertFalse(bitSet.anyLtMin(1, fixedData, null));
        Assert.assertTrue(bitSet.anyLtMax(1, fixedData, null));
        Assert.assertTrue(bitSet.anyGeMin(1, fixedData, null));
        bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        Assert.assertFalse(bitSet.anyLtMin(1, fixedData, null));
        Assert.assertFalse(bitSet.anyLtMax(1, fixedData, null));
        Assert.assertTrue(bitSet.anyGeMin(1, fixedData, null));
        bitSet.inc(1, 1, TransitionOp.overwrite, fixedData);
        Assert.assertFalse(bitSet.anyLtMin(1, fixedData, null));
        Assert.assertFalse(bitSet.anyLtMax(1, fixedData, null));
        Assert.assertFalse(bitSet.anyGeMin(1, fixedData, null));
    }
}
