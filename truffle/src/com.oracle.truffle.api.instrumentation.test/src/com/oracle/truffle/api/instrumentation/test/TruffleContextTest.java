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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.ThreadsActivationListener;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.lang.reflect.Method;

public class TruffleContextTest extends AbstractPolyglotTest {

    @Test
    public void testCreate() {
        setupEnv();

        TruffleContext tc = languageEnv.newContextBuilder().build();
        assertNotEquals(tc, languageEnv.getContext());
        assertFalse(tc.isEntered());
        assertFalse(tc.isClosed());
        assertNotNull(tc.toString());
        assertEquals(tc.getParent(), languageEnv.getContext());

        Object prev = tc.enter(null);
        assertTrue(tc.isEntered());
        assertFalse(tc.isClosed());
        tc.leave(null, prev);

        assertFalse(tc.isEntered());
        assertFalse(tc.isClosed());
    }

    @Test
    public void testSimpleForceClose() {
        setupEnv();

        TruffleContext tc = languageEnv.newContextBuilder().build();
        assertFalse(tc.isClosed());
        tc.closeCancelled(null, "testreason");
        assertTrue(tc.isClosed());
    }

    @Test
    public void testParallelForceClose() throws InterruptedException {
        setupEnv(Context.newBuilder().allowAllAccess(true).build(), new ProxyLanguage() {

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });

        TruffleContext tc = languageEnv.newContextBuilder().build();
        List<Thread> threads = new ArrayList<>();
        List<AtomicReference<Throwable>> exceptions = new ArrayList<>();
        Semaphore waitUntilStart = new Semaphore(0);
        for (int i = 0; i < 100; i++) {
            Thread t = languageEnv.createThread(() -> {
                com.oracle.truffle.api.source.Source s = com.oracle.truffle.api.source.Source.newBuilder(InstrumentationTestLanguage.ID, "EXPRESSION", "").build();
                CallTarget target = ProxyLanguage.getCurrentContext().getEnv().parsePublic(s);
                while (true) {
                    target.call();

                    // at least one thread should have started execution
                    waitUntilStart.release();
                }
            }, tc);
            AtomicReference<Throwable> exception = new AtomicReference<>();
            t.setUncaughtExceptionHandler((thread, e) -> {
                exception.set(e);
            });
            exceptions.add(exception);
            t.start();
            threads.add(t);
        }
        // 10s ought to be enough for anybody
        if (!waitUntilStart.tryAcquire(10000, TimeUnit.MILLISECONDS)) {
            for (AtomicReference<Throwable> e : exceptions) {
                if (e.get() != null) {
                    throw new AssertionError(e.get());
                }
            }
            throw new AssertionError("failed to wait for execution");
        }

        assertFalse(tc.isClosed());
        for (int i = 0; i < threads.size(); i++) {
            assertNull(exceptions.get(i).get());
        }
        tc.closeCancelled(null, "testreason");

        for (int i = 0; i < threads.size(); i++) {
            threads.get(i).join();
        }

        for (int i = 0; i < threads.size(); i++) {
            Throwable e = exceptions.get(i).get();
            assertNotNull(e);
            assertEquals(getCancelExcecutionClass(), e.getClass());
            assertEquals("testreason", e.getMessage());
            assertTrue(tc.isClosed());
        }

    }

    @Test
    public void testCloseInEntered() {
        setupEnv();

        TruffleContext tc = languageEnv.newContextBuilder().build();

        Node node = new Node() {
        };

        Object prev = tc.enter(null);

        assertFails(() -> tc.close(), IllegalStateException.class);

        assertFails(() -> tc.closeCancelled(node, "testreason"), getCancelExcecutionClass(), (e) -> {
            assertSame(getCancelExcecutionLocation(e), node);
            assertEquals("testreason", ((Throwable) e).getMessage());
        });

        assertFails(() -> tc.closeResourceExhausted(node, "testreason"), getCancelExcecutionClass(), (e) -> {
            assertSame(getCancelExcecutionLocation(e), node);
            assertEquals("testreason", ((Throwable) e).getMessage());
        });
        tc.leave(null, prev);
    }

    @Test
    public void testCancelledAndResourceExhausted() throws InterruptedException {
        setupEnv();

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                context.eval(InstrumentationTestLanguage.ID, "LOOP(infinity, STATEMENT)");
            } catch (Throwable e) {
                error.set(e);
            }
        });
        context.leave(); // avoid need for multi-threading

        AtomicReference<TruffleContext> enter = new AtomicReference<>();
        Semaphore waitUntilEntered = new Semaphore(0);
        instrumentEnv.getInstrumenter().attachThreadsActivationListener(new ThreadsActivationListener() {
            @TruffleBoundary
            public void onEnterThread(TruffleContext tc) {
                enter.set(tc);
                waitUntilEntered.release();
            }

            public void onLeaveThread(TruffleContext tc) {
            }
        });
        t.start();

        if (!waitUntilEntered.tryAcquire(10000, TimeUnit.MILLISECONDS)) {
            throw new AssertionError(error.get());
        }

        TruffleContext tc = enter.get();
        tc.closeResourceExhausted(null, "testError");
        t.join();

        assertNotNull(error.get());
        assertTrue(error.get().toString(), error.get() instanceof PolyglotException);
        PolyglotException e = (PolyglotException) error.get();
        assertEquals("testError", e.getMessage());
        assertTrue(e.isCancelled());
        assertTrue(e.isResourceExhausted());
    }

    @Test
    public void testContextHierarchy() {
        setupEnv();

        TruffleContext tc1 = languageEnv.newContextBuilder().build();
        TruffleContext tc2 = languageEnv.newContextBuilder().build();

        assertFalse(tc1.isActive());
        assertFalse(tc1.isEntered());
        assertFalse(tc2.isActive());
        assertFalse(tc2.isEntered());

        Object prev1 = tc1.enter(null);

        assertTrue(tc1.isActive());
        assertTrue(tc1.isEntered());
        assertFalse(tc2.isActive());
        assertFalse(tc2.isEntered());

        Object prev2 = tc2.enter(null);

        assertTrue(tc1.isActive());
        assertFalse(tc1.isEntered());
        assertTrue(tc2.isActive());
        assertTrue(tc2.isEntered());
        assertFails(() -> tc1.close(), IllegalStateException.class);
        assertFails(() -> tc1.closeCancelled(null, ""), IllegalStateException.class);
        assertFails(() -> tc1.closeResourceExhausted(null, ""), IllegalStateException.class);

        tc2.leave(null, prev2);

        assertTrue(tc1.isActive());
        assertTrue(tc1.isEntered());
        assertFalse(tc2.isActive());
        assertFalse(tc2.isEntered());

        tc1.leave(null, prev1);

        assertFalse(tc1.isActive());
        assertFalse(tc1.isEntered());
        assertFalse(tc2.isActive());
        assertFalse(tc2.isEntered());

        prev1 = tc1.enter(null);
        prev2 = tc2.enter(null);
        Object prev3 = tc1.enter(null);

        assertFails(() -> tc1.close(), IllegalStateException.class);

        // we allow cancel in this case. the error will be propagated an the caller
        // need to make sure to either propagate the cancel the parent context
        assertFails(() -> tc1.closeCancelled(null, ""), getCancelExcecutionClass());
        assertFails(() -> tc1.closeResourceExhausted(null, ""), getCancelExcecutionClass());

        tc1.leave(null, prev3);
        tc2.leave(null, prev2);
        tc1.leave(null, prev1);

    }

    private static Class<? extends Throwable> getCancelExcecutionClass() {
        try {
            return Class.forName("com.oracle.truffle.polyglot.PolyglotEngineImpl$CancelExecution").asSubclass(Throwable.class);
        } catch (ClassNotFoundException cnf) {
            throw new AssertionError("Cannot load CancelExecution class.", cnf);
        }
    }

    private static Node getCancelExcecutionLocation(Throwable t) {
        try {
            Method m = t.getClass().getDeclaredMethod("getLocation");
            m.setAccessible(true);
            return (Node) m.invoke(t);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke CancelExecution.getLocation.", e);
        }
    }
}
