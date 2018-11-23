/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class PushCallNodeTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testCallNodePickedByCallTargetCall() {
        CallTarget iterateFrames = create(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return captureStack();
            }
        });
        Node callLocation = new Node() {
        };

        CallTarget root = create(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return boundary(callLocation, () -> iterateFrames.call());
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
        Node callLocation = new Node() {
        };
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

    @Test
    @SuppressWarnings("unchecked")
    public void testDirectCallNodeClearedAssertion() {
        Node callLocation = new Node() {
        };
        Node prev = IndirectCallNode.pushCallLocation(callLocation);
        try {
            DirectCallNode callNode = DirectCallNode.create(create(RootNode.createConstantNode("")));
            assertAssertionError(() -> callNode.call(new Object[0]));
        } finally {
            IndirectCallNode.popCallLocation(prev);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIndirectCallNodeClearedAssertion() {
        Node callLocation = new Node() {
        };
        Node prev = IndirectCallNode.pushCallLocation(callLocation);
        try {
            IndirectCallNode callNode = IndirectCallNode.create();
            assertAssertionError(() -> callNode.call(create(RootNode.createConstantNode("")), new Object[0]));
        } finally {
            IndirectCallNode.popCallLocation(prev);
        }
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
        TruffleStackTraceElement.fillIn(e);
        return TruffleStackTraceElement.getStackTrace(e);
    }

    @TruffleBoundary
    public static Object boundary(Node callNode, Supplier<Object> run) {
        Node prev = IndirectCallNode.pushCallLocation(callNode);
        try {
            return run.get();
        } finally {
            IndirectCallNode.popCallLocation(prev);
        }
    }

}
