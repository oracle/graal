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
package jdk.graal.compiler.truffle.test.collection;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.runtime.collection.BTreeQueue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class BTreeQueueTest {
    @Test
    public void addSeveral() {
        final BTreeQueue<Integer> tree = new BTreeQueue<>();
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
        final BTreeQueue<Integer> tree = new BTreeQueue<>();
        final int total = 20;
        for (int i = 0; i < total; i++) {
            tree.add(i);
        }
        final Object[] elements = tree.toArray();
        for (int i = 0; i < total; i++) {
            Assert.assertEquals(i, elements[i]);
        }
    }

    private static BTreeQueue<Integer> testAddRandom(int total, boolean alwaysCheckInvariants) {
        Random rand = new Random(total * 141);
        int[] numbers = rand.ints().map(x -> x % (4 * total)).distinct().limit(total).toArray();
        return testAdd(total, alwaysCheckInvariants, numbers);
    }

    private static BTreeQueue<Integer> testAddSorted(int total, boolean alwaysCheckInvariants) {
        Random rand = new Random(total * 141);
        int[] numbers = rand.ints().map(x -> x % (4 * total)).distinct().limit(total).sorted().toArray();
        return testAdd(total, alwaysCheckInvariants, numbers);
    }

    private static BTreeQueue<Integer> testAddReverseSorted(int total, boolean alwaysCheckInvariants) {
        Random rand = new Random(total * 141);
        int[] numbers = rand.ints().map(x -> x % (4 * total)).distinct().limit(total).sorted().map(x -> x * -1).toArray();
        return testAdd(total, alwaysCheckInvariants, numbers);
    }

    private static BTreeQueue<Integer> testAdd(int total, boolean alwaysCheckInvariants, int[] numbers) {
        int smallest = Integer.MAX_VALUE;
        final BTreeQueue<Integer> tree = new BTreeQueue<>();
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
            Assert.assertEquals(i, tree.indexBefore(numbers[i]));
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

    private static void testPoll(BTreeQueue<Integer> tree, boolean alwaysCheckInvariants) {
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

    @Test
    public void addAndPoll() {
        testAddAndPoll(64, 4);
        testAddAndPoll(96, 7);
        testAddAndPoll(128, 4);
        testAddAndPoll(128, 10);
        testAddAndPoll(256, 12);
        testAddAndPoll(256, 32);
        testAddAndPoll(256, 64);
        testAddAndPoll(544, 6);
        testAddAndPoll(782, 10);
        testAddAndPoll(2560, 16);
        testAddAndPoll(32161, 32);
        testAddAndPoll(15400, 64);
        testAddAndPoll(44500, 128);
    }

    private static void testAddAndPoll(int until, int batchSize) {
        final ArrayList<Integer> observed = new ArrayList<>();
        final ArrayList<Integer> inserted = new ArrayList<>();
        final BTreeQueue<Integer> tree = new BTreeQueue<>();
        Random rand = new Random(until * batchSize);
        for (int i = 0; i < until; i += batchSize - 1) {
            for (int j = 0; j < i % (batchSize / 2); j++) {
                if (rand.nextBoolean()) {
                    int x = i + j + batchSize / 2;
                    tree.add(x);
                    inserted.add(x);
                } else {
                    int x = i + j;
                    tree.add(x);
                    inserted.add(x);
                }
            }
            for (int j = 0; j < i * i % (batchSize / 2) && tree.size() > 0; j++) {
                observed.add(tree.poll());
            }
        }
        while (tree.size() > 0) {
            observed.add(tree.poll());
        }
        Collections.sort(inserted);
        Assert.assertEquals(inserted.size(), observed.size());
        for (int i = 0; i < inserted.size(); i++) {
            Assert.assertEquals(inserted.get(i), observed.get(i));
        }
    }

    @Test
    public void addIndexOf() {
        final BTreeQueue<Integer> tree = new BTreeQueue<>();
        for (int i = 0; i < 1024; i++) {
            Assert.assertEquals(i, tree.addIndexOf(i));
        }
    }

    @Test
    public void indexBefore() {
        final BTreeQueue<Integer> tree = new BTreeQueue<>();
        Assert.assertEquals(0, tree.indexBefore(0));
        Assert.assertEquals(-1, tree.indexOf(0));
        for (int i = 0; i < 1411; i++) {
            Assert.assertEquals(i, tree.addIndexOf(i * 2));
            Assert.assertEquals(i, tree.indexBefore(i * 2 - 1));
            Assert.assertEquals(i, tree.indexBefore(i * 2));
            Assert.assertEquals(i + 1, tree.indexBefore(i * 2 + 1));
            Assert.assertEquals(-1, tree.indexOf(i * 2 - 1));
            Assert.assertEquals(i, tree.indexOf(i * 2));
            Assert.assertEquals(-1, tree.indexOf(i * 2 + 1));
        }
    }

    @Test
    public void removeEmpty() {
        final BTreeQueue<Integer> tree = new BTreeQueue<>();
        Assert.assertEquals(0, tree.size());
        Assert.assertEquals(null, tree.poll());
        tree.add(11);
        Assert.assertEquals(1, tree.size());
        Assert.assertEquals((Integer) 11, tree.poll());
        Assert.assertEquals(null, tree.poll());
        Assert.assertEquals(null, tree.poll());
    }

    @Test
    public void removeManyEmpty() {
        final BTreeQueue<Integer> tree = new BTreeQueue<>();
        for (int count = 1; count < 16000; count *= 2) {
            for (int i = 0; i < count; i++) {
                tree.add(i);
            }
            for (int i = 0; i < count; i++) {
                Assert.assertEquals((Integer) i, tree.poll());
            }
            for (int i = 0; i < 5; i++) {
                Assert.assertEquals(null, tree.poll());
            }
        }
    }
}
