/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.nodes;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Iterator;

import org.junit.Test;

import com.oracle.truffle.api.TestingLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;

public class NodeUtilTest {

    @Test
    public void testRecursiveIterator1() {
        TestRootNode root = new TestRootNode();
        root.child0 = new TestNode();
        root.adoptChildren();

        int count = iterate(NodeUtil.makeRecursiveIterator(root));

        assertThat(count, is(2));
        assertThat(root.visited, is(0));
        assertThat(root.child0.visited, is(1));
    }

    private static int iterate(Iterator<Node> iterator) {
        int iterationCount = 0;
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node == null) {
                continue;
            }
            if (node instanceof TestNode) {
                ((TestNode) node).visited = iterationCount;
            } else if (node instanceof TestRootNode) {
                ((TestRootNode) node).visited = iterationCount;
            } else {
                throw new AssertionError();
            }
            iterationCount++;
        }
        return iterationCount;
    }

    private static class TestNode extends Node {

        @Child TestNode child0;
        @Child TestNode child1;

        private int visited;

        TestNode() {
        }

    }

    private static class TestRootNode extends RootNode {

        @Child TestNode child0;

        private int visited;

        TestRootNode() {
            super(TestingLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

    }

}
