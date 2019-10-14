/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.nodes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

public class NodeAdoptableTest {

    static class DefaultNode extends Node {

        @Child Node child;

        @Override
        public boolean isAdoptable() {
            return super.isAdoptable();
        }

        <T extends Node> T insertPublic(T node) {
            child = insert(node);
            return node;
        }

    }

    static class NonAdoptibleNode extends Node {
        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    @Test
    public void testDefaultNode() {
        DefaultNode node = new DefaultNode();
        assertTrue(node.isAdoptable());
        DefaultNode parentNode = new DefaultNode();
        parentNode.insertPublic(node);
        assertSame(parentNode, node.getParent());
    }

    @Test
    public void testNonAdoptableNode() {
        DefaultNode parentNode = new DefaultNode();
        NonAdoptibleNode node = new NonAdoptibleNode();
        assertFalse(node.isAdoptable());
        parentNode.insertPublic(node);
        assertNull(node.getParent());

        NonAdoptibleNode newNode = new NonAdoptibleNode();
        assertFalse(node.isSafelyReplaceableBy(newNode));
        try {
            node.replace(newNode);
            fail();
        } catch (IllegalStateException e) {
            assertEquals("This node cannot be replaced, because it does not yet have a parent.", e.getMessage());
        }

        // replacement with explicit parent works with no parent pointers updated
        assertTrue(NodeUtil.isReplacementSafe(parentNode, node, newNode));
        assertTrue(NodeUtil.replaceChild(parentNode, node, newNode));
        assertSame(newNode, parentNode.child);
        assertNull(newNode.getParent());
        assertNull(node.getParent());
    }
}
