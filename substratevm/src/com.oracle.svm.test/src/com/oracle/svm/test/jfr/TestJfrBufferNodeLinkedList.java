/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.word.WordFactory;
import org.junit.Test;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrBuffer;
import com.oracle.svm.core.jfr.JfrBufferAccess;
import com.oracle.svm.core.jfr.JfrBufferList;
import com.oracle.svm.core.jfr.JfrBufferNode;
import com.oracle.svm.core.jfr.JfrBufferNodeAccess;
import com.oracle.svm.core.jfr.JfrBufferType;

public class TestJfrBufferNodeLinkedList {

    @Test
    public void testBasicAdditionAndRemoval() {
        final int nodeCount = 10;
        final JfrBufferList list = new JfrBufferList();
        addNodes(list, nodeCount);
        int count = countNodes(list);
        assertEquals("Number of nodes in list does not match nodes added.", count, nodeCount);
        cleanUpList(list);
    }

    @Test
    public void testMiddleRemoval() {
        final int nodeCount = 10;
        JfrBufferList list = new JfrBufferList();
        addNodes(list, nodeCount);
        removeNthNode(list, nodeCount / 2);
        assertEquals("Removal from middle failed", countNodes(list), nodeCount - 1);
        cleanUpList(list);
    }

    @Test
    public void testConcurrentAddition() throws Exception {
        final int nodeCountPerThread = 10;
        final int threads = 10;
        JfrBufferList list = new JfrBufferList();
        Runnable r = () -> {
            addNodes(list, nodeCountPerThread);
        };
        Stressor.execute(threads, r);
        assertEquals("Incorrect number of nodes added", countNodes(list), nodeCountPerThread * threads);
        cleanUpList(list);
    }

    private static void cleanUpList(JfrBufferList list) {
        JfrBufferNode node = removeAllNodes(list);
        assertTrue("Could not remove all nodes", node.isNull());
    }

    private static void addNodes(JfrBufferList list, int nodeCount) {
        for (int i = 0; i < nodeCount; i++) {
            JfrBuffer buffer = JfrBufferAccess.allocate(WordFactory.unsigned(32), JfrBufferType.THREAD_LOCAL_NATIVE);
            list.addNode(buffer);
        }
    }

    @Uninterruptible(reason = "Locking with no transition.")
    private static int countNodes(JfrBufferList list) {
        int count = 0;
        JfrBufferNode node = list.getHead();
        while (node.isNonNull()) {
            count++;
            node = node.getNext();
        }
        return count;
    }

    @Uninterruptible(reason = "Locking with no transition.")
    private static JfrBufferNode removeAllNodes(JfrBufferList list) {
        // Try removing the nodes
        JfrBufferNode node = list.getHead();
        while (node.isNonNull()) {
            JfrBufferNode next = node.getNext();
            JfrBufferAccess.free(node.getBuffer());
            /*
             * Once JfrBufferNodeAccess.setRetired(node) is called, another thread may free the node
             * at any time.
             */
            JfrBufferNodeAccess.setRetired(node);
            list.removeNode(node, WordFactory.nullPointer());
            node = next;
        }
        return node;
    }

    @Uninterruptible(reason = "Locking with no transition.")
    private static void removeNthNode(JfrBufferList list, int target) {
        JfrBufferNode prev = WordFactory.nullPointer();
        JfrBufferNode node = list.getHead();
        int count = 0;
        while (node.isNonNull()) {
            JfrBufferNode next = node.getNext();
            if (count == target) {
                JfrBufferAccess.free(node.getBuffer());
                /*
                 * Once JfrBufferNodeAccess.setRetired(node) is called, another thread may free the
                 * node at any time.
                 */
                JfrBufferNodeAccess.setRetired(node);
                list.removeNode(node, prev);
                break;
            }
            prev = node;
            node = next;
            count++;
        }
    }
}
