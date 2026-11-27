/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.junit.nativeimage;

import static com.oracle.svm.test.junit.nativeimage.GCTestUtils.allocateGarbage;
import static com.oracle.svm.test.junit.nativeimage.GCTestUtils.awaitGC;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.shared.NeverInline;

import jdk.graal.compiler.api.directives.GraalDirectives;

/**
 * Tests that the garbage collector never damages the object graph.
 *
 * A collector may move objects around in memory and must then update every reference that pointed to
 * them. If it misses one, or updates one incorrectly, the program is left with a reference to the
 * wrong place. The symptom is usually a crash or nonsense data much later, far away from the real
 * bug, which makes it hard to find. These tests make the damage visible immediately: they build data
 * structures with known contents, request collections, and then check node indexes, array values,
 * and map lookups.
 *
 * The tests are written for any garbage collector: they only use {@link System#gc()} and allocation
 * to provoke collections, and they make no assumption about when a collection happens. The one test
 * that waits for the collector to reclaim cyclic garbage is skipped with the Epsilon GC, which never
 * collects.
 */
public class GCObjectGraphTest {

    /**
     * Builds a long chain of objects, collects, then walks the whole chain.
     *
     * Each node is checked twice: that it appears in the right position (its stored index matches how
     * far along the chain we are) and that the chain has exactly the expected length. If the GC moved
     * a node but failed to update the {@code next} field pointing at it, the walk would go off to the
     * wrong object and one of these checks would fail.
     */
    @Test
    public void testLinkedChainSurvivesGC() {
        final int length = 10_000;
        Node head = new Node(0);
        Node current = head;
        for (int i = 1; i < length; i++) {
            current.next = new Node(i);
            current = current.next;
        }

        collectRepeatedly(5);

        int position = 0;
        for (Node n = head; n != null; n = n.next) {
            Assert.assertEquals("node at position " + position + " has the wrong index after GC", position, n.index);
            position++;
        }
        Assert.assertEquals("the chain lost or gained nodes during GC", length, position);
    }

    /**
     * Same idea as the chain test, but for references stored in an array instead of in object fields,
     * because a collector updates those through a different code path.
     *
     * The array is filled with strings whose contents are known from their position, so a reference
     * that ends up pointing at the wrong object is detected immediately.
     */
    @Test
    public void testObjectArrayContentsSurviveGC() {
        final int size = 20_000;
        String[] array = new String[size];
        for (int i = 0; i < size; i++) {
            array[i] = "element-" + i;
        }

        collectRepeatedly(5);

        for (int i = 0; i < size; i++) {
            Assert.assertEquals("array slot " + i + " changed during GC", "element-" + i, array[i]);
        }
    }

    /**
     * Checks a real-world data structure rather than a hand-built one. A {@link HashMap} stores its
     * entries in an internal array of entry objects that themselves reference keys and values, so a
     * single missed reference update usually makes lookups fail.
     *
     * After collecting, every key must still find its value, and the map must still report the same
     * size.
     */
    @Test
    public void testHashMapContentsSurviveGC() {
        final int entries = 5_000;
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < entries; i++) {
            map.put("key-" + i, i);
        }

        collectRepeatedly(5);

        Assert.assertEquals("the map changed size during GC", entries, map.size());
        for (int i = 0; i < entries; i++) {
            Assert.assertEquals("lookup of key-" + i + " failed after GC", Integer.valueOf(i), map.get("key-" + i));
        }
    }

    /**
     * Reuses the same array across collection requests, replacing each slot with a newly allocated
     * node in every round. After the final request, checks that each slot still holds the value
     * written in the last round.
     */
    @Test
    public void testReferenceUpdatesDuringGCAreTracked() {
        final int slots = 2_000;
        final int rounds = 10;
        Node[] holder = new Node[slots];

        for (int round = 0; round < rounds; round++) {
            /* Overwrite every slot with a freshly allocated node, dropping the previous one. */
            for (int i = 0; i < slots; i++) {
                holder[i] = new Node(round * slots + i);
            }
            allocateGarbage(4);
            System.gc();
        }

        /* Only the nodes written in the final round may still be referenced. */
        int base = (rounds - 1) * slots;
        for (int i = 0; i < slots; i++) {
            Assert.assertNotNull("slot " + i + " unexpectedly became null", holder[i]);
            Assert.assertEquals("slot " + i + " does not hold the last value written to it", base + i, holder[i].index);
        }
    }

    /**
     * Builds structures where objects reference each other in a cycle, then drops them entirely.
     *
     * Cycles are worth testing because a collector that freed memory by counting references would
     * never reclaim them. To make the outcome visible, a weak reference watches every node of every
     * cycle. Once the cycles are unreachable, the collector must clear all of these references.
     * Meanwhile the still-live structure must stay intact.
     */
    @Test
    public void testCyclicGarbageIsCollectedAndLiveDataSurvives() {
        /* This list is kept alive for the whole test and must stay valid. */
        List<Node> live = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            live.add(new Node(i));
        }

        List<WeakReference<Node>> cycleNodes = new ArrayList<>();
        for (int round = 0; round < 20; round++) {
            createCycle(cycleNodes);
            System.gc();
        }

        awaitGC(() -> cycleNodes.stream().allMatch(r -> r.refersTo(null)), "cyclic garbage was not collected");

        Assert.assertEquals("the live list changed size", 100, live.size());
        for (int i = 0; i < live.size(); i++) {
            Assert.assertEquals("live element " + i + " was damaged", i, live.get(i).index);
        }
    }

    /**
     * A stress test with a mixture of object sizes and lifetimes, closer to how a real program
     * behaves than the focused tests above.
     *
     * Most allocations are immediately abandoned, while every hundredth object is kept. At the end,
     * the kept objects are verified. Collectors often treat large and small objects differently, so
     * mixing sizes covers more of the allocation paths.
     */
    @Test
    public void testMixedAllocationStressKeepsSurvivorsIntact() {
        final int iterations = 50_000;
        List<byte[]> survivors = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            /* Vary the size so that both small objects and larger arrays are exercised. */
            int size = (i % 50 == 0) ? 8 * 1024 : 32;
            byte[] data = new byte[size];
            /* Write a recognizable value so corruption can be detected later. */
            data[0] = (byte) i;
            if (i % 100 == 0) {
                survivors.add(data);
            } else {
                /* Without this, the compiler could remove the allocation of an array nobody uses. */
                GraalDirectives.blackhole(data);
            }
        }
        System.gc();

        Assert.assertEquals("unexpected number of survivors", iterations / 100, survivors.size());
        for (int i = 0; i < survivors.size(); i++) {
            byte[] data = survivors.get(i);
            int originalIteration = i * 100;
            Assert.assertEquals("survivor " + i + " has corrupted contents", (byte) originalIteration, data[0]);
        }
    }

    /** A node in a linked chain. Each node stores its own index so it can be verified later. */
    private static final class Node {
        private final int index;
        private Node next;

        Node(int index) {
            this.index = index;
        }
    }

    /**
     * Creates two nodes that point at each other and then returns, so the pair is unreachable but
     * still referenced from within itself. A weak reference to each node is added to
     * {@code watchers}, so that the caller can check whether the pair gets collected. Weak
     * references do not keep the nodes alive. They also make the nodes escape, so the compiler cannot
     * remove the allocations.
     */
    @NeverInline("Keep references to the nodes out of the caller's frame.")
    private static void createCycle(List<WeakReference<Node>> watchers) {
        Node a = new Node(-1);
        Node b = new Node(-2);
        a.next = b;
        b.next = a;
        watchers.add(new WeakReference<>(a));
        watchers.add(new WeakReference<>(b));
    }

    /** Requests several collections, allocating in between so that collectors make progress. */
    private static void collectRepeatedly(int rounds) {
        for (int i = 0; i < rounds; i++) {
            allocateGarbage(8);
            System.gc();
        }
    }
}
