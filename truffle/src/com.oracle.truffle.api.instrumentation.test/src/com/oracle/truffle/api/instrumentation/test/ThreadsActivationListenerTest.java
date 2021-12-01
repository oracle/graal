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
package com.oracle.truffle.api.instrumentation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ThreadsActivationListener;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext;

public class ThreadsActivationListenerTest extends AbstractPolyglotTest {

    public ThreadsActivationListenerTest() {
        enterContext = false; // allows to test manual enters
        cleanupOnSetup = false; // allows to create multiple contexts
        needsLanguageEnv = true;
        needsInstrumentEnv = true;
    }

    @Test
    public void testInternalContext() {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return RootNode.createConstantNode(42).getCallTarget();
            }
        });

        Context c0 = this.context;
        TruffleContext tc0 = this.languageEnv.getContext();

        c0.enter();
        TruffleContext ic0 = this.languageEnv.newContextBuilder().build();
        TruffleContext ic0CreatorHandle = ic0;
        Object prev = ic0.enter(null);
        // look language handle on the context it is not the same as
        // the creator handle. The creator handle can be closed.
        ic0 = LanguageContext.get(null).getEnv().getContext();
        ic0.leave(null, prev);

        c0.leave();

        List<TruffleContext> entered = new ArrayList<>();
        List<TruffleContext> left = new ArrayList<>();
        EventBinding<?> binding = instrumentEnv.getInstrumenter().attachThreadsActivationListener(new ThreadsActivationListener() {

            @TruffleBoundary
            public void onEnterThread(TruffleContext c) {
                entered.add(c);
            }

            @TruffleBoundary
            public void onLeaveThread(TruffleContext c) {
                left.add(c);
            }
        });

        assertList(entered);
        assertList(left);

        assertSame(tc0, ic0.getParent());

        assertList(entered);
        assertList(left);

        prev = ic0.enter(null);
        assertList(entered, ic0);
        assertList(left);

        ic0.leave(null, prev);

        assertList(entered, ic0);
        assertList(left, ic0);

        prev = tc0.enter(null);
        assertList(entered, ic0, tc0);
        assertList(left, ic0);
        Object prev1 = ic0.enter(null);
        assertList(entered, ic0, tc0, ic0);
        assertList(left, ic0);

        ic0.leave(null, prev1);
        assertList(entered, ic0, tc0, ic0);
        assertList(left, ic0, ic0);

        tc0.leave(null, prev);
        assertList(entered, ic0, tc0, ic0);
        assertList(left, ic0, ic0, tc0);

        binding.dispose();
        prev = tc0.enter(null);
        prev1 = ic0.enter(null);
        ic0.leave(null, prev1);
        tc0.leave(null, prev);

        assertList(entered, ic0, tc0, ic0);
        assertList(left, ic0, ic0, tc0);

        ic0CreatorHandle.close();
    }

    @Test
    public void testMultiContext() {
        Engine engine = Engine.create();
        setupEnv(Context.newBuilder().engine(engine).build(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return RootNode.createConstantNode(42).getCallTarget();
            }
        });
        Context c0 = this.context;
        TruffleContext tc0 = this.languageEnv.getContext();

        setupEnv(Context.newBuilder().engine(engine).build(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return RootNode.createConstantNode(42).getCallTarget();
            }
        });

        Context c1 = this.context;
        TruffleContext tc1 = languageEnv.getContext();

        List<TruffleContext> entered = new ArrayList<>();
        List<TruffleContext> left = new ArrayList<>();

        EventBinding<?> binding = instrumentEnv.getInstrumenter().attachThreadsActivationListener(new ThreadsActivationListener() {
            @TruffleBoundary
            public void onEnterThread(TruffleContext c) {
                entered.add(c);
            }

            @TruffleBoundary
            public void onLeaveThread(TruffleContext c) {
                left.add(c);
            }
        });

        assertList(entered);
        assertList(left);

        c0.enter();

        assertList(entered, tc0);
        assertList(left);
        c1.enter();

        assertList(entered, tc0, tc1);
        assertList(left);

        c1.leave();
        assertList(entered, tc0, tc1);
        assertList(left, tc1);

        c0.leave();

        assertList(entered, tc0, tc1);
        assertList(left, tc1, tc0);

        binding.dispose();

        c0.enter();
        c1.enter();
        c1.leave();
        c0.leave();

        assertList(entered, tc0, tc1);
        assertList(left, tc1, tc0);

        c0.close();
        c1.close();
        engine.close();
    }

    @Test
    public void testSingleContext() {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return RootNode.createConstantNode(42).getCallTarget();
            }
        });
        List<TruffleContext> entered = new ArrayList<>();
        List<TruffleContext> left = new ArrayList<>();
        EventBinding<?> binding = instrumentEnv.getInstrumenter().attachThreadsActivationListener(new ThreadsActivationListener() {
            @TruffleBoundary
            public void onEnterThread(TruffleContext c) {
                entered.add(c);
            }

            @TruffleBoundary
            public void onLeaveThread(TruffleContext c) {
                left.add(c);
            }

        });
        assertList(entered);
        assertList(left);
        context.enter();

        TruffleContext to = languageEnv.getContext();
        assertList(entered, to);
        assertList(left);

        context.leave();
        assertList(entered, to);
        assertList(left, to);

        context.enter();
        assertList(entered, to, to);
        assertList(left, to);
        context.enter();
        assertList(entered, to, to, to);
        assertList(left, to);

        context.leave();
        assertList(entered, to, to, to);
        assertList(left, to, to);
        context.leave();
        assertList(entered, to, to, to);
        assertList(left, to, to, to);

        context.eval(ProxyLanguage.ID, "");
        assertList(entered, to, to, to, to);
        assertList(left, to, to, to, to);

        binding.dispose();
        context.enter();
        context.leave();
        assertList(entered, to, to, to, to);
        assertList(left, to, to, to, to);
    }

    @Test
    public void testMultiThreading() throws InterruptedException {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return RootNode.createConstantNode(42).getCallTarget();
            }

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });
        List<TruffleContext> entered = new ArrayList<>();
        List<TruffleContext> left = new ArrayList<>();
        instrumentEnv.getInstrumenter().attachThreadsActivationListener(new ThreadsActivationListener() {
            @TruffleBoundary
            public void onEnterThread(TruffleContext c) {
                synchronized (entered) {
                    entered.add(c);
                }
            }

            @TruffleBoundary
            public void onLeaveThread(TruffleContext c) {
                synchronized (left) {
                    left.add(c);
                }
            }

        });
        assertList(entered);
        assertList(left);
        TruffleContext to = languageEnv.getContext();

        context.enter();
        assertList(entered, to);
        assertList(left);

        Thread t = new Thread(new Runnable() {
            public void run() {
                assertList(entered, to);
                assertList(left);
                context.enter();
                assertList(entered, to, to);
                assertList(left);
                context.leave();
                assertList(entered, to, to);
                assertList(left, to);
            }
        });
        t.start();
        t.join();

        context.leave();
        assertList(entered, to, to);
        assertList(left, to, to);
    }

    @Test
    public void testExceptionFromOnEnterThread() {
        testExceptionImpl(() -> {
            try {
                context.enter();
                fail("Expected a PolyglotException");
            } catch (PolyglotException pe) {
                assertTrue(pe.isInternalError());
            }
            assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        }, true, false);
    }

    @Test
    public void testExceptionFromOnLeaveThread() {
        testExceptionImpl(() -> {
            context.enter();
            try {
                context.leave();
                fail("Expected a PolyglotException");
            } catch (PolyglotException pe) {
                assertTrue(pe.isInternalError());
            }
            assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        }, false, true);
    }

    @Test
    public void testExceptionFromBoth() {
        testExceptionImpl(() -> {
            try {
                context.enter();
                fail("Expected a PolyglotException");
            } catch (PolyglotException pe) {
                assertTrue(pe.isInternalError());
            }
            assertFails(() -> Context.getCurrent(), IllegalStateException.class);
        }, true, true);
    }

    private void testExceptionImpl(Runnable body, boolean throwOnEnter, boolean throwOnLeave) {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return RootNode.createConstantNode(42).getCallTarget();
            }
        });

        ThreadsActivationListener listener = new ThreadsActivationListener() {
            @Override
            public void onEnterThread(TruffleContext c) {
                if (throwOnEnter) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new RuntimeException();
                }
            }

            @Override
            public void onLeaveThread(TruffleContext c) {
                if (throwOnLeave) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new RuntimeException();
                }
            }
        };
        EventBinding<? extends ThreadsActivationListener> binding = instrumentEnv.getInstrumenter().attachThreadsActivationListener(listener);
        try {
            body.run();
        } finally {
            binding.dispose();
        }
    }

    private static void assertList(List<TruffleContext> list, TruffleContext... expectedContexts) {
        assertEquals(expectedContexts.length, list.size());
        for (int i = 0; i < expectedContexts.length; i++) {
            assertEquals(expectedContexts[i], list.get(i));
        }
    }
}
