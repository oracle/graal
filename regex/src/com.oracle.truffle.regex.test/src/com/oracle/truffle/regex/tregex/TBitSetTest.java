/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex;

import com.oracle.truffle.regex.util.TBitSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

public class TBitSetTest {

    private BitSetOracle oracle;
    private TBitSet bitSet;

    @Before
    public void setUp() {
        reset();
    }

    private void reset() {
        oracle = new BitSetOracle(256);
        bitSet = new TBitSet(256);
    }

    private void check() {
        boolean empty = true;
        for (int i = 0; i < 256; i++) {
            Assert.assertEquals("mismatch at " + i, oracle.get(i), bitSet.get(i));
            if (oracle.get(i)) {
                empty = false;
            }
        }
        Assert.assertEquals(empty, bitSet.isEmpty());
        Iterator<Integer> oracleIterator = oracle.iterator();
        Iterator<Integer> bitSetIterator = bitSet.iterator();
        while (oracleIterator.hasNext()) {
            Assert.assertTrue(bitSetIterator.hasNext());
            Assert.assertEquals(oracleIterator.next(), bitSetIterator.next());
        }
    }

    private static BitSetOracle rangeOracle(int lo, int hi) {
        BitSetOracle ret = new BitSetOracle(256);
        ret.setRange(lo, hi);
        return ret;
    }

    private static BitSetOracle setOracle(int... values) {
        BitSetOracle ret = new BitSetOracle(256);
        ret.setMulti(values);
        return ret;
    }

    private static TBitSet bitSetFromOracle(BitSetOracle o) {
        TBitSet bs = new TBitSet(256);
        for (int i : o) {
            bs.set(i);
        }
        return bs;
    }

    @Test
    public void testEmpty() {
        check();
    }

    @Test
    public void testSetAndClear() {
        checkSet(0);
        checkSet(34);
        checkSet(127);
        checkSet(128);
        checkSet(129);
        checkSet(255);
        checkClear(0);
        checkClear(34);
        checkClear(127);
        checkClear(128);
        checkClear(129);
        checkClear(255);
        checkSet(127);
        checkSet(128);
        checkSet(129);
        checkSet(255);
        checkClear(128);
        checkClear(255);
    }

    @Test
    public void testSetRange() {
        checkSetRange(0, 63);
        reset();
        checkSetRange(0, 64);
        reset();
        checkSetRange(0, 65);
        reset();
        checkSetRange(1, 63);
        reset();
        checkSetRange(1, 64);
        reset();
        checkSetRange(1, 65);
        reset();
        checkSetRange(0, 127);
        reset();
        checkSetRange(0, 128);
        reset();
        checkSetRange(0, 129);
        reset();
        checkSetRange(1, 127);
        reset();
        checkSetRange(1, 128);
        reset();
        checkSetRange(1, 129);
        reset();
        checkSetRange(50, 70);
        checkSetRange(0, 35);
        checkSetRange(200, 255);
    }

    @Test
    public void testInvert() {
        checkInvert();
        checkInvert();
        checkSet(0);
        checkInvert();
        checkInvert();
        checkSet(255);
        checkInvert();
        checkInvert();
        checkSet(127);
        checkInvert();
        checkInvert();
        checkSet(128);
        checkInvert();
        checkInvert();
    }

    @Test
    public void testIntersect() {
        checkSetRange(30, 150);
        checkIntersect(rangeOracle(90, 180));
        reset();
        checkSetRange(30, 150);
        checkIntersect(rangeOracle(90, 140));
        reset();
        checkSetRange(0, 150);
        checkIntersect(rangeOracle(150, 160));
        reset();
        checkSetRange(30, 150);
        checkIntersect(rangeOracle(0, 30));
        reset();
        checkSetRange(30, 150);
        checkIntersect(rangeOracle(0, 29));
        reset();
        checkSetRange(30, 150);
        checkIntersect(rangeOracle(151, 255));
        reset();
        checkSetRange(30, 150);
        checkIntersect(setOracle(0, 5, 29, 30, 111, 150, 180, 255));
        reset();
    }

    @Test
    public void testUnion() {
        checkSetRange(30, 150);
        checkUnion(rangeOracle(90, 180));
        reset();
        checkSetRange(30, 150);
        checkUnion(rangeOracle(90, 140));
        reset();
        checkSetRange(0, 150);
        checkUnion(rangeOracle(150, 160));
        reset();
        checkSetRange(30, 150);
        checkUnion(rangeOracle(0, 30));
        reset();
        checkSetRange(30, 150);
        checkUnion(rangeOracle(0, 29));
        reset();
        checkSetRange(30, 150);
        checkUnion(rangeOracle(151, 255));
        reset();
        checkSetRange(30, 150);
        checkUnion(setOracle(0, 5, 29, 30, 111, 150, 180, 255));
        reset();
    }

    @Test
    public void testSubtract() {
        checkSetRange(30, 150);
        checkSubtract(rangeOracle(90, 180));
        reset();
        checkSetRange(30, 150);
        checkSubtract(rangeOracle(90, 140));
        reset();
        checkSetRange(0, 150);
        checkSubtract(rangeOracle(150, 160));
        reset();
        checkSetRange(30, 150);
        checkSubtract(rangeOracle(0, 30));
        reset();
        checkSetRange(30, 150);
        checkSubtract(rangeOracle(0, 29));
        reset();
        checkSetRange(30, 150);
        checkSubtract(rangeOracle(151, 255));
        reset();
        checkSetRange(30, 150);
        checkSubtract(setOracle(0, 5, 29, 30, 111, 150, 180, 255));
        reset();
    }

    @Test
    public void testEquals() {
        TBitSet small = new TBitSet(128);
        TBitSet large = new TBitSet(256);
        Assert.assertTrue(small.equals(large));
        Assert.assertTrue(large.equals(small));
        small.set(42);
        Assert.assertFalse(small.equals(large));
        Assert.assertFalse(large.equals(small));
        large.set(42);
        Assert.assertTrue(small.equals(large));
        Assert.assertTrue(large.equals(small));
        large.set(211);
        Assert.assertFalse(small.equals(large));
        Assert.assertFalse(large.equals(small));
    }

    private void checkSet(int i) {
        oracle.set(i);
        bitSet.set(i);
        check();
    }

    private void checkSetRange(int lo, int hi) {
        oracle.setRange(lo, hi);
        bitSet.setRange(lo, hi);
        check();
    }

    private void checkClear(int i) {
        oracle.clear(i);
        bitSet.clear(i);
        check();
    }

    private void checkInvert() {
        oracle.invert();
        bitSet.invert();
        check();
    }

    private void checkIntersect(BitSetOracle o) {
        oracle.intersect(o);
        bitSet.intersect(bitSetFromOracle(o));
        check();
    }

    private void checkSubtract(BitSetOracle o) {
        oracle.subtract(o);
        bitSet.subtract(bitSetFromOracle(o));
        check();
    }

    private void checkUnion(BitSetOracle o) {
        oracle.union(o);
        bitSet.union(bitSetFromOracle(o));
        check();
    }
}
