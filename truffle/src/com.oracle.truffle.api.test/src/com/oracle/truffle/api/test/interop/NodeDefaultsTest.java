/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import org.graalvm.polyglot.Context;

public class NodeDefaultsTest extends InteropLibraryBaseTest {

    @Test
    public void testScopeDefaults() {
        Node n = new TestNode();
        NodeLibrary l = createLibrary(NodeLibrary.class, n);
        assertFalse(l.hasScope(n, null));
        assertFails(() -> l.getScope(n, null, true), UnsupportedMessageException.class);
        assertFails(() -> l.getScope(n, null, false), UnsupportedMessageException.class);
    }

    @Test
    public void testReceiverDefaults() {
        Node n = new TestNode();
        NodeLibrary l = createLibrary(NodeLibrary.class, n);
        assertFalse(l.hasReceiverMember(n, null));
        assertFails(() -> l.getReceiverMember(n, null), UnsupportedMessageException.class);
    }

    @Test
    public void testRootInstanceDefaults() {
        Node n = new TestNode();
        NodeLibrary l = createLibrary(NodeLibrary.class, n);
        assertFalse(l.hasRootInstance(n, null));
        assertFails(() -> l.getRootInstance(n, null), UnsupportedMessageException.class);
    }

    @Test
    @SuppressWarnings("hiding")
    public void testViewDefaults() {
        try (Context context = Context.create()) {
            ProxyLanguage language = new ProxyLanguage();
            ProxyLanguage.setDelegate(language);
            context.initialize(ProxyLanguage.ID);
            context.enter();
            Frame frame = Truffle.getRuntime().createMaterializedFrame(new Object[]{});
            Node n = new TestNode(ProxyLanguage.get(null), frame.getFrameDescriptor());
            NodeLibrary l = createLibrary(NodeLibrary.class, n);
            Object v = 42 * 42;
            // Integer is not associated with a language
            assertFails(() -> l.getView(n, frame, v), AssertionError.class);
            Object pv = new ProxyLanguageValue();
            assertSame(pv, l.getView(n, frame, pv));
        }
    }

    @GenerateWrapper
    static class TestNode extends Node implements InstrumentableNode {
        TestNode() {
            this(null, null);
        }

        @SuppressWarnings("unused")
        TestNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            new RootNode(language, frameDescriptor) {
                @Child Node node = insert(TestNode.this);

                @Override
                public Object execute(VirtualFrame frame) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new TestNodeWrapper(this, probe);
        }

        public void execute(@SuppressWarnings("unused") VirtualFrame f) {
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class ProxyLanguageValue implements TruffleObject {

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "Proxy value";
        }
    }
}
