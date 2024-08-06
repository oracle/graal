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

import com.oracle.truffle.runtime.collection.ArrayQueue;
import com.oracle.truffle.runtime.collection.DelegatingBlockingQueue;

import java.util.ArrayList;

public class DelegatingBlockingQueueTest {

    @Test
    public void testAddItems() {
        DelegatingBlockingQueue<Integer> queue = new DelegatingBlockingQueue<>(new ArrayQueue<>());
        for (int i = 100; i < 1000; i++) {
            Assert.assertEquals(i - 100, queue.size());
            Assert.assertTrue(queue.add(i));
            Assert.assertEquals(i - 100 + 1, queue.size());
        }
        final Object[] elements = queue.toArray();
        for (int i = 100; i < 1000; i++) {
            Assert.assertEquals(i, elements[i - 100]);
        }
    }

    @Test
    public void testRemoveItems() {
        DelegatingBlockingQueue<Integer> queue = new DelegatingBlockingQueue<>(new ArrayQueue<>());
        for (int i = 0; i < 1000; i++) {
            Assert.assertEquals(i, queue.size());
            Assert.assertTrue(queue.add(i));
            Assert.assertEquals(i + 1, queue.size());
        }
        ArrayList<Integer> results = new ArrayList<>();
        while (!queue.isEmpty()) {
            results.add(queue.poll());
        }
        for (int i = 0; i < 1000; i++) {
            Assert.assertEquals((Integer) i, results.get(i));
        }
    }

    @Test
    public void testAddRemove() {
        DelegatingBlockingQueue<Integer> queue = new DelegatingBlockingQueue<>(new ArrayQueue<>());
        for (int i = 0; i < 10000; i++) {
            queue.add(i);
            queue.add(i + 1);
            queue.add(i + 2);
            Assert.assertEquals((Integer) i, queue.remove());
            Assert.assertEquals((Integer) (i + 1), queue.remove());
            Assert.assertEquals((Integer) (i + 2), queue.remove());
        }
        Assert.assertEquals(0, queue.size());
        Assert.assertEquals(128, queue.internalCapacity());
    }
}
