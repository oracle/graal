/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.collection;

import org.graalvm.compiler.truffle.runtime.collection.BTree;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

public class BTreeTest {
    @Test
    public void addSeveral() {
        final BTree<Integer> tree = new BTree<>();
        tree.add(1);
        tree.add(6);
        tree.add(5);
        tree.add(4);
        tree.add(2);
        tree.add(3);
        tree.add(0);
        final Object[] elements = tree.toArray();
        for (int i = 0; i < 7; i++) {
            Assert.assertEquals(i, elements[i]);
        }
    }

    @Test
    public void addMoreThanSingleNode() {
        final BTree<Integer> tree = new BTree<>();
        final int total = 20;
        for (int i = 0; i < total; i++) {
            tree.add(i);
        }
        final Object[] elements = tree.toArray();
        for (int i = 0; i < total; i++) {
            Assert.assertEquals(i, elements[i]);
        }
    }

    private BTree<Integer> testAddRandom(int total, boolean alwaysCheckInvariants) {
        Random rand = new Random(total * 141);
        int[] numbers = rand.ints().map(x -> x % (4 * total)).distinct().limit(total).toArray();
        return test(total, alwaysCheckInvariants, numbers);
    }

    private BTree<Integer> testAddSorted(int total, boolean alwaysCheckInvariants) {
        Random rand = new Random(total * 141);
        int[] numbers = rand.ints().map(x -> x % (4 * total)).distinct().limit(total).sorted().toArray();
        return test(total, alwaysCheckInvariants, numbers);
    }

    private BTree<Integer> testAddReverseSorted(int total, boolean alwaysCheckInvariants) {
        Random rand = new Random(total * 141);
        int[] numbers = rand.ints().map(x -> x % (4 * total)).distinct().limit(total).sorted().map(x -> x * -1).toArray();
        return test(total, alwaysCheckInvariants, numbers);
    }

    private BTree<Integer> test(int total, boolean alwaysCheckInvariants, int[] numbers) {
        int smallest = Integer.MAX_VALUE;
        final BTree<Integer> tree = new BTree<>();
        for (int i = 0; i < total; i++) {
            tree.add(numbers[i]);
            if (alwaysCheckInvariants) {
                tree.checkInvariants();
            }
            smallest = Math.min(smallest, numbers[i]);
            Assert.assertEquals((Integer) smallest, tree.peek());
        }
        tree.checkInvariants();
        Arrays.sort(numbers);
        final Object[] elements = tree.toArray();
        for (int i = 0; i < total; i++) {
            Assert.assertEquals(numbers[i], elements[i]);
        }
        Assert.assertEquals((Integer) numbers[0], tree.peek());
        return tree;
    }

    @Test
    public void addMoreThanSingleNodeRandomOrder() {
        testAddRandom(20, true);
    }

    @Test
    public void addOneLevel() {
        testAddRandom(31, true);
        testAddRandom(44, true);
        testAddRandom(61, true);
        testAddRandom(72, true);
        testAddRandom(96, true);
        testAddRandom(101, true);
        testAddRandom(125, true);
        testAddRandom(131, true);
        testAddRandom(139, true);
        testAddSorted(79, true);
        testAddReverseSorted(51, true);
    }

    @Test
    public void addTwoLevels() {
        testAddRandom(199, true);
        testAddRandom(256, true);
        testAddRandom(384, true);
        testAddRandom(421, true);
        testAddRandom(512, true);
        testAddRandom(525, true);
        testAddRandom(599, true);
        testAddRandom(614, true);
        testAddRandom(731, true);
        testAddRandom(777, true);
        testAddRandom(852, true);
        testAddRandom(941, true);
        testAddSorted(414, true);
        testAddSorted(714, true);
        testAddSorted(814, true);
        testAddSorted(1014, true);
        testAddReverseSorted(541, true);
        testAddReverseSorted(794, true);
        testAddReverseSorted(901, true);
        testAddReverseSorted(1055, true);
    }

    @Test
    public void addMany() {
        for (int i = 1024; i < 128000; i += 1000) {
            testAddRandom(i, false);
            testAddSorted(i + 1, false);
            testAddReverseSorted(i + 2, false);
        }
    }

    private void testPoll(BTree<Integer> tree, boolean alwaysCheckInvariants) {
        final Object[] elements = tree.toArray();
        int i = 0;
        tree.checkInvariants();
        while (tree.size() > 0) {
            Integer cur = tree.poll();
            Assert.assertEquals(elements[i], cur);
            if (alwaysCheckInvariants) {
                tree.checkInvariants();
            }
            i++;
        }
    }

    @Test
    public void pollFew() {
        testPoll(testAddRandom(11, true), true);
        testPoll(testAddSorted(12, true), true);
        testPoll(testAddReverseSorted(14, true), true);
    }

    @Test
    public void pollOneLevel() {
        testPoll(testAddRandom(43, true), true);
        testPoll(testAddReverseSorted(56, true), true);
    }

    @Test
    public void pollTwoLevels() {
        testPoll(testAddRandom(384, true), true);
        testPoll(testAddRandom(453, true), true);
        testPoll(testAddRandom(518, true), true);
        testPoll(testAddRandom(761, true), true);
        testPoll(testAddRandom(914, true), true);
    }

    @Test
    public void pollMany() {
        for (int i = 1561; i < 128000; i += 1000) {
            testPoll(testAddRandom(i, false), false);
        }
    }
}
