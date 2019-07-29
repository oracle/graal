/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex;

import com.oracle.truffle.regex.util.CompilationFinalBitSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

public class CompilationFinalBitSetTest {

    private BitSetOracle oracle;
    private CompilationFinalBitSet bitSet;

    @Before
    public void setUp() {
        reset();
    }

    private void reset() {
        oracle = new BitSetOracle(256);
        bitSet = new CompilationFinalBitSet(256);
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

    private static CompilationFinalBitSet bitSetFromOracle(BitSetOracle o) {
        CompilationFinalBitSet bs = new CompilationFinalBitSet(256);
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
