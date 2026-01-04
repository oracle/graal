/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public class LanguageSystemThreadTest {

    @Test
    public void testCreateSystemThread() {
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, CreateSystemThreadLanguage.class, "");
        }
    }

    @Registration
    public static final class CreateSystemThreadLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            AtomicIntegerArray res = new AtomicIntegerArray(2);
            Thread t1 = env.createSystemThread(() -> res.set(0, 1));
            Thread t2 = env.createSystemThread(() -> res.set(1, 1));
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            Assert.assertEquals(1, res.get(0));
            Assert.assertEquals(1, res.get(1));
            return null;
        }
    }

    @Test
    public void testNonTerminatedSystemThread() {
        try (Engine engine = Engine.create()) {
            Context context1 = Context.newBuilder().engine(engine).build();
            long threadId = AbstractExecutableTestLanguage.evalTestLanguage(context1, NonTerminatedSystemThreadLanguage.class, "").asLong();
            AbstractPolyglotTest.assertFails((Runnable) context1::close, PolyglotException.class, (e) -> {
                Assert.assertTrue(e.isInternalError());
                Assert.assertEquals(
                                String.format("java.lang.IllegalStateException: The context has an alive system thread Forgotten thread created by language %s.",
                                                TestUtils.getDefaultLanguageId(NonTerminatedSystemThreadLanguage.class)),
                                e.getMessage());
            });
            try (Context context2 = Context.newBuilder().engine(engine).build()) {
                AbstractExecutableTestLanguage.parseTestLanguage(context2, ReleaseThreadLanguage.class, "").execute(threadId);
            }
        }
    }

    @Registration
    public static final class NonTerminatedSystemThreadLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            CountDownLatch systemThreadActive = new CountDownLatch(1);
            Thread t1 = env.createSystemThread(() -> {
                systemThreadActive.countDown();
                try {
                    park();
                } catch (InterruptedException ie) {
                    // pass
                }
            });
            t1.setName("Forgotten thread");
            t1.start();
            systemThreadActive.await();
            return threadId(t1);
        }
    }

    @Registration
    public static final class ReleaseThreadLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            long tid = (long) frameArguments[0];
            unpark(tid);
            return null;
        }
    }

    @Test
    public void testCreateSystemThreadAfterDispose() {
        try (Context context = Context.newBuilder().build()) {
            Value errorMessage = AbstractExecutableTestLanguage.evalTestLanguage(context, CreateSystemThreadAfterDisposeLanguage.class, "");
            Assert.assertTrue(errorMessage.isString());
            Assert.assertEquals(
                            String.format("Context is already closed. Cannot start a new system thread for language %s.", TestUtils.getDefaultLanguageId(CreateSystemThreadAfterDisposeLanguage.class)),
                            errorMessage.asString());
        }
    }

    @Registration
    public static final class CreateSystemThreadAfterDisposeLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext truffleContext = env.newInnerContextBuilder().initializeCreatorContext(true).inheritAllAccess(true).build();
            ExecutableContext executableContext = null;
            Object prev = truffleContext.enter(node);
            try {
                executableContext = TestAPIAccessor.engineAccess().getCurrentContext(getClass());
                truffleContext.closeCancelled(node, "close");
            } catch (ThreadDeath t) {
                try {
                    executableContext.env.createSystemThread(() -> {
                    });
                } catch (IllegalStateException e) {
                    return e.getMessage();
                }
            } finally {
                truffleContext.leave(node, prev);
            }
            return null;
        }
    }

    @Test
    public void testStartSystemThreadAfterDispose() {
        try (Context context = Context.newBuilder().build()) {
            Value errorMessage = AbstractExecutableTestLanguage.evalTestLanguage(context, StartSystemThreadAfterDisposeLanguage.class, "");
            Assert.assertTrue(errorMessage.isString());
            Assert.assertEquals(
                            String.format("Context is already closed. Cannot start a new system thread for language %s.", TestUtils.getDefaultLanguageId(StartSystemThreadAfterDisposeLanguage.class)),
                            errorMessage.asString());
        }
    }

    @Registration
    public static final class StartSystemThreadAfterDisposeLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            TruffleContext truffleContext = env.newInnerContextBuilder().initializeCreatorContext(true).inheritAllAccess(true).build();
            Object prev = truffleContext.enter(node);
            Thread systemThread = null;
            try {
                ExecutableContext executableContext = TestAPIAccessor.engineAccess().getCurrentContext(getClass());
                systemThread = executableContext.env.createSystemThread(() -> {
                });
                systemThread.setUncaughtExceptionHandler((t, e) -> {
                    throwableRef.set(e);
                });
                truffleContext.closeCancelled(node, "close");
            } catch (ThreadDeath t) {
                systemThread.start();
                systemThread.join();
                return throwableRef.get() != null ? throwableRef.get().getMessage() : null;
            } finally {
                truffleContext.leave(node, prev);
            }
            return null;
        }
    }

    @Test
    public void testEnterInSystemThread() {
        try (Context context1 = Context.newBuilder().build()) {
            String errorMessage = AbstractExecutableTestLanguage.evalTestLanguage(context1, EnterInSystemThread.class, "").asString();
            Assert.assertEquals("Context cannot be entered on system threads.", errorMessage);
        }
    }

    @Registration
    public static final class EnterInSystemThread extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext context = env.getContext();
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Thread t = env.createSystemThread(() -> {
                try {
                    context.enter(null);
                    Assert.fail("Should not reach here");
                } catch (Throwable exception) {
                    throwableRef.set(exception);
                }
            });
            t.start();
            t.join();
            return throwableRef.get().getMessage();
        }
    }

    @Test
    public void testCreateSystemThreadNotEntered() {
        try (Context context1 = Context.newBuilder().build()) {
            String successMessage = AbstractExecutableTestLanguage.evalTestLanguage(context1, CreateSystemThreadNotEnteredLanguage.class, "").asString();
            Assert.assertEquals("OK", successMessage);
        }
    }

    @Test
    public void testCreateNewThreadNotEntered() {
        try (Context context1 = Context.newBuilder().build()) {
            String errorMessage = AbstractExecutableTestLanguage.evalTestLanguage(context1, CreateNewThreadNotEnteredLanguage.class, "").asString();
            Assert.assertEquals("Not entered in an Env's context.", errorMessage);
        }
    }

    @Registration
    public static final class CreateSystemThreadNotEnteredLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Thread t = env.createSystemThread(() -> {
                try {
                    Thread systemT = env.createSystemThread(() -> {
                    });
                    // Can create system thread from a system thread.
                    Assert.assertNotNull(systemT);
                } catch (Throwable exception) {
                    throwableRef.set(exception);
                }
            });
            t.start();
            t.join();
            Throwable throwable = throwableRef.get();
            if (throwable != null) {
                return throwable.getMessage();
            } else {
                return "OK";
            }
        }
    }

    @Registration
    public static final class CreateNewThreadNotEnteredLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Thread t = new Thread(() -> {
                try {
                    env.createSystemThread(() -> {
                    });
                    Assert.fail("Should not reach here");
                } catch (Throwable exception) {
                    throwableRef.set(exception);
                }
            });
            t.start();
            t.join();
            return throwableRef.get().getMessage();
        }
    }

    @SuppressWarnings("deprecation")
    private static long threadId(Thread thread) {
        return thread.getId();
    }

    private static final Set<Long> blockedThreads = new HashSet<>();

    private static void park() throws InterruptedException {
        Thread current = Thread.currentThread();
        long id = threadId(current);
        synchronized (blockedThreads) {
            blockedThreads.add(id);
            while (blockedThreads.contains(id)) {
                blockedThreads.wait();
            }
        }
    }

    private static void unpark(long tid) {
        synchronized (blockedThreads) {
            blockedThreads.remove(tid);
            blockedThreads.notifyAll();
        }
    }
}
