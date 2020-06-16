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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

public class ScopedViewTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    @ExportLibrary(InteropLibrary.class)
    static class OtherScopedLanguageObject implements TruffleObject {

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return OtherTestLanguage.class;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "other";
        }

    }

    static class TestInstrumentableNode extends Node implements InstrumentableNode {

        public boolean isInstrumentable() {
            return true;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            return null;
        }

    }

    static class TestOtherNode extends Node {

    }

    static class TestNotInstrumentableNode extends Node implements InstrumentableNode {

        public boolean isInstrumentable() {
            return false;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            return null;
        }

    }

    static class TestRootNode extends RootNode {

        protected TestRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Child Node child;

        public void setChild(Node child) {
            this.child = insert(child);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

    }

    static TestRootNode createRoot(TruffleLanguage<?> lang) {
        TestRootNode root = new TestRootNode(lang);
        Truffle.getRuntime().createCallTarget(new TestRootNode(lang));
        return root;
    }

    @Test
    public void testValidRequest() throws UnsupportedMessageException {
        AtomicInteger count = new AtomicInteger();
        setupEnv(Context.create(), new ProxyLanguage() {

            @Override
            protected Object getScopedView(LanguageContext c, Node location, Frame frame, Object value) {
                count.incrementAndGet();
                try {
                    assertSame(ProxyLanguage.class, createLibrary(InteropLibrary.class, value).getLanguage(value));
                } catch (UnsupportedMessageException e) {
                    fail();
                }
                return super.getScopedView(c, location, frame, value);
            }

        });
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        Node location = new TestInstrumentableNode();
        TestRootNode root = createRoot(language);
        root.setChild(location);
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], root.getRootNode().getFrameDescriptor());
        Object scopedView = instrumentEnv.getScopedView(l, location, frame, "");
        assertSame(ProxyLanguage.class, createLibrary(InteropLibrary.class, scopedView).getLanguage(scopedView));
        assertEquals(1, count.get());
    }

    @Test
    public void testWrongRootLanguage() {
        setupEnv();
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        Node location = new TestInstrumentableNode();
        TestRootNode root = createRoot(null);
        root.setChild(location);
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], root.getRootNode().getFrameDescriptor());
        assertFails(() -> instrumentEnv.getScopedView(l, location, frame, ""), IllegalArgumentException.class);
    }

    @Test
    public void testUnadoptedNode() {
        setupEnv();
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        Node location = new TestInstrumentableNode();
        TestRootNode root = createRoot(language);
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], root.getRootNode().getFrameDescriptor());
        assertFails(() -> instrumentEnv.getScopedView(l, location, frame, ""), IllegalArgumentException.class);
    }

    @Test
    public void testNotInstrumentable() {
        setupEnv();
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        Node location = new TestNotInstrumentableNode();
        TestRootNode root = createRoot(language);
        root.setChild(location);
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], root.getRootNode().getFrameDescriptor());
        assertFails(() -> instrumentEnv.getScopedView(l, location, frame, ""), IllegalArgumentException.class);
    }

    @Test
    public void testOtherNode() {
        setupEnv();
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        Node location = new TestOtherNode();
        TestRootNode root = createRoot(language);
        root.setChild(location);
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], root.getRootNode().getFrameDescriptor());
        assertFails(() -> instrumentEnv.getScopedView(l, location, frame, ""), IllegalArgumentException.class);
    }

    @Test
    public void testInvalidFrame() {
        setupEnv();
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        Node location = new TestInstrumentableNode();
        TestRootNode root = createRoot(language);
        root.setChild(location);
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], createRoot(language).getRootNode().getFrameDescriptor());
        assertFails(() -> instrumentEnv.getScopedView(l, location, frame, ""), IllegalArgumentException.class);
    }

    @Test
    public void testWrongLanguage() {
        setupEnv();
        context.initialize(OtherTestLanguage.ID);
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        Node location = new TestInstrumentableNode();
        TestRootNode root = createRoot(OtherTestLanguage.getInstance());
        root.setChild(location);
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], createRoot(language).getRootNode().getFrameDescriptor());
        assertFails(() -> instrumentEnv.getScopedView(l, location, frame, ""), IllegalArgumentException.class);
    }

    @Test
    public void testReturnWrongLanguage() {
        setupEnv(Context.create(), new ProxyLanguage() {

            @Override
            protected Object getScopedView(LanguageContext c, Node location, Frame frame, Object value) {
                return new OtherScopedLanguageObject();
            }

        });
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        Node location = new TestInstrumentableNode();
        TestRootNode root = createRoot(language);
        root.setChild(location);
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], root.getRootNode().getFrameDescriptor());
        assertFails(() -> instrumentEnv.getScopedView(l, location, frame, ""), AssertionError.class);
    }

    @Test
    public void testReturnNull() {
        setupEnv(Context.create(), new ProxyLanguage() {

            @Override
            protected Object getScopedView(LanguageContext c, Node location, Frame frame, Object value) {
                return null;
            }

        });
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        Node location = new TestInstrumentableNode();
        TestRootNode root = createRoot(language);
        root.setChild(location);
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], root.getRootNode().getFrameDescriptor());
        assertFails(() -> instrumentEnv.getScopedView(l, location, frame, ""), NullPointerException.class);
    }

}
