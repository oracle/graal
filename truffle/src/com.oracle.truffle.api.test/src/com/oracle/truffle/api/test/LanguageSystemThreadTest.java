/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

public class LanguageSystemThreadTest {

    @Test
    public void testCreateSystemThread() {
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestCreateSystemThreadLanguage.class, "");
        }
    }

    @Registration
    public static final class TestCreateSystemThreadLanguage extends AbstractExecutableTestLanguage {

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
            long threadId = AbstractExecutableTestLanguage.evalTestLanguage(context1, TestNonTerminatedSystemThreadLanguage.class, "").asLong();
            AbstractPolyglotTest.assertFails((Runnable) context1::close, IllegalStateException.class, (e) -> {
                Assert.assertEquals(
                                "The context has an alive system thread Forgotten thread created by language com_oracle_truffle_api_test_languagesystemthreadtest_testnonterminatedsystemthreadlanguage.",
                                e.getMessage());
            });
            try (Context context2 = Context.newBuilder().engine(engine).build()) {
                AbstractExecutableTestLanguage.parseTestLanguage(context2, ReleaseThreadLanguage.class, "").execute(threadId);
            }
        }
    }

    @Registration
    public static final class TestNonTerminatedSystemThreadLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            CountDownLatch systemThreadActive = new CountDownLatch(1);
            Thread t1 = env.createSystemThread(() -> {
                systemThreadActive.countDown();
                try {
                    GlobalTestData.INSTANCE.await();
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

    @Test
    public void testCreateSystemThreadAfterDispose() {
        try (Engine engine = Engine.create()) {
            try (Context context1 = Context.newBuilder().engine(engine).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(context1, StoreEnvLanguage.class, "");
            }
            try (Context context2 = Context.newBuilder().engine(engine).build()) {
                Value errorMessage = AbstractExecutableTestLanguage.evalTestLanguage(context2, TestCreateSystemThreadAfterDisposeLanguage.class, "");
                Assert.assertTrue(errorMessage.isString());
                Assert.assertEquals("Context is already closed. Cannot start a new system thread for language com_oracle_truffle_api_test_languagesystemthreadtest_storeenvlanguage.",
                                errorMessage.asString());
            }
        }
    }

    @Registration
    public static final class StoreEnvLanguage extends AbstractExecutableTestLanguage {

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            GlobalTestData.INSTANCE.objectInClosedContext = env;
            return null;
        }
    }

    @Registration
    public static final class TestCreateSystemThreadAfterDisposeLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try {
                ((TruffleLanguage.Env) GlobalTestData.INSTANCE.objectInClosedContext).createSystemThread(() -> {
                });
                return null;
            } catch (IllegalStateException ise) {
                return ise.getMessage();
            }
        }
    }

    @Test
    public void testStartSystemThreadAfterDispose() {
        try (Engine engine = Engine.create()) {
            try (Context context1 = Context.newBuilder().engine(engine).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(context1, StoreThreadLanguage.class, "");
            }
            try (Context context2 = Context.newBuilder().engine(engine).build()) {
                Value errorMessage = AbstractExecutableTestLanguage.evalTestLanguage(context2, TestStartSystemThreadAfterDisposeLanguage.class, "");
                Assert.assertTrue(errorMessage.isString());
                Assert.assertEquals("Context is already closed. Cannot start a new system thread for language com_oracle_truffle_api_test_languagesystemthreadtest_storethreadlanguage.",
                                errorMessage.asString());
            }
        }
    }

    @Registration
    public static final class StoreThreadLanguage extends AbstractExecutableTestLanguage {

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            GlobalTestData.INSTANCE.objectInClosedContext = env.createSystemThread(() -> {
            });
            return null;
        }
    }

    @Registration
    public static final class TestStartSystemThreadAfterDisposeLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Thread thread = (Thread) GlobalTestData.INSTANCE.objectInClosedContext;
            thread.setUncaughtExceptionHandler((t, e) -> {
                throwableRef.set(e);
            });
            thread.start();
            thread.join();
            Throwable throwable = throwableRef.get();
            return throwable != null ? throwable.getMessage() : null;
        }
    }

    private static long threadId(Thread thread) {
        return thread.getId();
    }

    private static final class GlobalTestData {

        static final GlobalTestData INSTANCE = new GlobalTestData();

        private final Set<Long> blockedThreads = new HashSet<>();

        Object objectInClosedContext;

        private GlobalTestData() {
        }

        synchronized void await() throws InterruptedException {
            Thread current = Thread.currentThread();
            long id = threadId(current);
            blockedThreads.add(id);
            while (blockedThreads.contains(id)) {
                wait();
            }
        }

        synchronized void release(long tid) {
            blockedThreads.remove(tid);
            notifyAll();
        }
    }

    @Registration
    public static final class ReleaseThreadLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            long tid = (long) frameArguments[0];
            GlobalTestData.INSTANCE.release(tid);
            return null;
        }
    }
}
