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

import com.oracle.truffle.api.Truffle;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

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

public class NodeAssertionsTest extends InteropLibraryBaseTest {

    @GenerateWrapper
    @ExportLibrary(NodeLibrary.class)
    static class TestAssertsNode extends Node implements InstrumentableNode {

        boolean hasScope;
        boolean hasReceiverMember;
        boolean hasRootInstance;
        Function<Frame, Object> getScope;
        Function<Frame, Object> getReceiverMember;
        Function<Frame, Object> getRootInstance;
        BiFunction<Frame, Object, Object> getView;

        TestAssertsNode() {
            this(null, null);
        }

        @SuppressWarnings("unused")
        TestAssertsNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            new RootNode(language, frameDescriptor) {
                @Child Node node = insert(TestAssertsNode.this);

                @Override
                public Object execute(VirtualFrame frame) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @ExportMessage
        final boolean hasScope(@SuppressWarnings("unused") Frame frame) {
            return hasScope;
        }

        @ExportMessage
        final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) throws UnsupportedMessageException {
            if (getScope != null) {
                return getScope.apply(frame);
            }
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        final boolean hasReceiverMember(@SuppressWarnings("unused") Frame frame) {
            return hasReceiverMember;
        }

        @ExportMessage
        final Object getReceiverMember(Frame frame) throws UnsupportedMessageException {
            if (getReceiverMember != null) {
                return getReceiverMember.apply(frame);
            }
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        boolean hasRootInstance(@SuppressWarnings("unused") Frame frame) {
            return hasRootInstance;
        }

        @ExportMessage
        final Object getRootInstance(Frame frame) throws UnsupportedMessageException {
            if (getRootInstance != null) {
                return getRootInstance.apply(frame);
            }
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        final Object getView(Frame frame, Object value) {
            if (getView != null) {
                return getView.apply(frame, value);
            }
            return value;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new TestAssertsNodeWrapper(this, probe);
        }

        public void execute(@SuppressWarnings("unused") VirtualFrame f) {
        }
    }

    @Test
    public void testScope() throws UnsupportedMessageException {
        TestAssertsNode n = new TestAssertsNode();
        NodeLibrary l = createLibrary(NodeLibrary.class, n);
        Object invalidScope = new TruffleObject() {
        };
        Object validScope = new ValidScope();

        n.hasScope = false;
        n.getScope = null;
        assertFalse(l.hasScope(n, null));
        assertFails(() -> l.getScope(n, null, true), UnsupportedMessageException.class);

        n.hasScope = false;
        n.getScope = (frame) -> validScope;
        assertFails(() -> l.hasScope(n, null), AssertionError.class);
        assertFails(() -> l.getScope(n, null, true), AssertionError.class);

        n.hasScope = true;
        n.getScope = null;
        assertFails(() -> l.hasScope(n, null), AssertionError.class);
        assertFails(() -> l.getScope(n, null, true), AssertionError.class);

        n.hasScope = true;
        n.getScope = frame -> validScope;
        assertTrue(l.hasScope(n, null));
        assertSame(validScope, l.getScope(n, null, true));

        n.hasScope = true;
        n.getScope = frame -> invalidScope;
        assertFails(() -> l.hasScope(n, null), AssertionError.class);
        assertFails(() -> l.getScope(n, null, true), AssertionError.class);
    }

    @Test
    public void testReceiver() throws UnsupportedMessageException {
        TestAssertsNode n = new TestAssertsNode();
        NodeLibrary l = createLibrary(NodeLibrary.class, n);
        Object invalidReceiverName = new Object();
        Object validReceiverName = "receiver";

        n.hasReceiverMember = false;
        n.getReceiverMember = null;
        assertFalse(l.hasReceiverMember(n, null));
        assertFails(() -> l.getReceiverMember(n, null), UnsupportedMessageException.class);

        n.hasReceiverMember = false;
        n.getReceiverMember = (frame) -> validReceiverName;
        assertFails(() -> l.hasReceiverMember(n, null), AssertionError.class);
        assertFails(() -> l.getReceiverMember(n, null), AssertionError.class);

        n.hasReceiverMember = true;
        n.getReceiverMember = null;
        assertFails(() -> l.hasReceiverMember(n, null), AssertionError.class);
        assertFails(() -> l.getReceiverMember(n, null), AssertionError.class);

        n.hasReceiverMember = true;
        n.getReceiverMember = (frame) -> validReceiverName;
        assertTrue(l.hasReceiverMember(n, null));
        assertSame(validReceiverName, l.getReceiverMember(n, null));

        n.hasReceiverMember = true;
        n.getReceiverMember = (frame) -> invalidReceiverName;
        assertFails(() -> l.hasReceiverMember(n, null), AssertionError.class);
        assertFails(() -> l.getReceiverMember(n, null), AssertionError.class);

        n.hasReceiverMember = true;
        n.getReceiverMember = (frame) -> validReceiverName;
        assertTrue(l.hasReceiverMember(n, null));
        assertSame(validReceiverName, l.getReceiverMember(n, null));
    }

    @Test
    public void testRootInstance() throws UnsupportedMessageException {
        TestAssertsNode n = new TestAssertsNode();
        NodeLibrary l = createLibrary(NodeLibrary.class, n);
        Object invalidRootInstance = new TruffleObject() {
        }; // not executable
        Object validRootInstance = new ValidRootInstance();

        n.hasRootInstance = false;
        n.getRootInstance = null;
        assertFalse(l.hasRootInstance(n, null));
        assertFails(() -> l.getRootInstance(n, null), UnsupportedMessageException.class);

        n.hasRootInstance = false;
        n.getRootInstance = (frame) -> validRootInstance;
        assertFails(() -> l.hasRootInstance(n, null), AssertionError.class);
        assertFails(() -> l.getRootInstance(n, null), AssertionError.class);

        n.hasRootInstance = true;
        n.getRootInstance = null;
        assertFails(() -> l.hasRootInstance(n, null), AssertionError.class);
        assertFails(() -> l.getRootInstance(n, null), AssertionError.class);

        n.hasRootInstance = true;
        n.getRootInstance = frame -> validRootInstance;
        assertTrue(l.hasRootInstance(n, null));
        assertSame(validRootInstance, l.getRootInstance(n, null));

        n.hasRootInstance = true;
        n.getRootInstance = frame -> invalidRootInstance;
        assertFails(() -> l.hasRootInstance(n, null), AssertionError.class);
        assertFails(() -> l.getRootInstance(n, null), AssertionError.class);
    }

    @Test
    @SuppressWarnings("hiding")
    public void testView() {
        try (Context context = Context.create()) {
            ProxyLanguage language = new ProxyLanguage();
            ProxyLanguage.setDelegate(language);
            context.initialize(ProxyLanguage.ID);
            context.enter();
            Frame frame = Truffle.getRuntime().createMaterializedFrame(new Object[]{});
            TestAssertsNode n = new TestAssertsNode(ProxyLanguage.get(null), frame.getFrameDescriptor());
            NodeLibrary l = createLibrary(NodeLibrary.class, n);

            n.getView = (f, x) -> x;
            assertFails(() -> l.getView(n, null, true), AssertionError.class);
            assertFails(() -> l.getView(n, null, new Object()), AssertionError.class);
            assertFails(() -> l.getView(n, frame, true), AssertionError.class);
            assertFails(() -> l.getView(n, frame, new Object()), AssertionError.class);
            NodeDefaultsTest.ProxyLanguageValue pv = new NodeDefaultsTest.ProxyLanguageValue();
            assertSame(pv, l.getView(n, frame, pv));
            assertFails(() -> l.getView(n, null, pv), AssertionError.class);
            assertFails(() -> l.getView(new TestAssertsNode(ProxyLanguage.get(null), null), frame, pv), AssertionError.class);
            assertFails(() -> l.getView(new TestAssertsNode(null, frame.getFrameDescriptor()), frame, pv), AssertionError.class);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ValidScope implements TruffleObject {

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isScope() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return null;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "local";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ValidRootInstance implements TruffleObject {

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object execute(@SuppressWarnings("unused") Object[] arguments) {
            throw new UnsupportedOperationException();
        }
    }
}
