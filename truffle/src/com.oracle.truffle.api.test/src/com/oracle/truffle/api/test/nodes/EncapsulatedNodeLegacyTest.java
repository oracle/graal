/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("deprecation")
public class EncapsulatedNodeLegacyTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testCallNodePickedByCallTargetCall() {
        CallTarget iterateFrames = create(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return captureStack();
            }
        });
        Node callLocation = adopt(new Node() {
        });

        CallTarget root = create(new RootNode(null) {

            @Override
            public Object execute(VirtualFrame frame) {
                return boundary(callLocation, () -> IndirectCallNode.getUncached().call(iterateFrames, new Object[0]));
            }
        });
        List<TruffleStackTraceElement> frames = (List<TruffleStackTraceElement>) root.call();
        assertEquals(2, frames.size());
        Iterator<TruffleStackTraceElement> iterator = frames.iterator();
        TruffleStackTraceElement frame;

        frame = iterator.next();
        assertSame(iterateFrames, frame.getTarget());
        assertNull(frame.getLocation());

        frame = iterator.next();
        assertSame(root, frame.getTarget());
        assertSame(callLocation, frame.getLocation());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCallNodePickedWithoutCallTarget() {
        Node callLocation = adopt(new Node() {
        });
        CallTarget root = create(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return boundary(callLocation, () -> captureStack());
            }
        });

        List<TruffleStackTraceElement> frames = (List<TruffleStackTraceElement>) root.call();
        assertEquals(1, frames.size());
        TruffleStackTraceElement frame = frames.iterator().next();
        assertSame(root, frame.getTarget());
        assertNull(frame.getLocation());
    }

    private static <T extends Node> T adopt(T node) {
        RootNode root = new RootNode(null) {
            {
                insert(node);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        };
        root.adoptChildren();
        return node;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNotAdoptedNode() {
        Node node = new Node() {
        };

        assertAssertionError(() -> NodeUtil.pushEncapsulatingNode(node));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNotAdoptableNode() {
        Node node = new Node() {
            @Override
            public boolean isAdoptable() {
                return false;
            }
        };
        assertAssertionError(() -> NodeUtil.pushEncapsulatingNode(node));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPartiallyAdoptedNode() {
        Node node = new Node() {
        };
        Node otherNode = new Node() {

            @Child private Node innerNode = node;

        };
        otherNode.adoptChildren();

        assertAssertionError(() -> NodeUtil.pushEncapsulatingNode(node));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNullAllowed() {
        Node prev = NodeUtil.pushEncapsulatingNode(null);
        assertNull(prev);
        NodeUtil.popEncapsulatingNode(null);
    }

    @Test
    public void testGetEncapsulatingNode() {
        Node node = NodeUtil.getCurrentEncapsulatingNode();
        assertNull(node);
        Node node0 = adopt(new Node() {
        });
        Node node1 = adopt(new Node() {
        });

        Node prev0 = NodeUtil.pushEncapsulatingNode(node0);
        assertSame(null, prev0);
        assertSame(node0, NodeUtil.getCurrentEncapsulatingNode());

        Node prev1 = NodeUtil.pushEncapsulatingNode(node1);
        assertSame(node0, prev1);
        assertSame(node1, NodeUtil.getCurrentEncapsulatingNode());
        NodeUtil.popEncapsulatingNode(prev1);

        assertSame(node0, NodeUtil.getCurrentEncapsulatingNode());
        NodeUtil.popEncapsulatingNode(prev0);

        assertSame(null, NodeUtil.getCurrentEncapsulatingNode());
    }

    private static void assertAssertionError(Runnable r) {
        try {
            r.run();
        } catch (AssertionError e) {
            return;
        }
        fail();
    }

    static CallTarget create(RootNode root) {
        return Truffle.getRuntime().createCallTarget(root);
    }

    @TruffleBoundary
    private static Object captureStack() {
        Exception e = new Exception();
        TruffleStackTrace.fillIn(e);
        return TruffleStackTrace.getStackTrace(e);
    }

    @TruffleBoundary
    public static Object boundary(Node callNode, Supplier<Object> run) {
        Node prev = NodeUtil.pushEncapsulatingNode(callNode);
        try {
            return run.get();
        } finally {
            NodeUtil.popEncapsulatingNode(prev);
        }
    }

}
