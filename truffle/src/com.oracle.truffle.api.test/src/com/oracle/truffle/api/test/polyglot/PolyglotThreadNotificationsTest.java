/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.NullObject;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

@SuppressWarnings("hiding")
@RunWith(Parameterized.class)
public class PolyglotThreadNotificationsTest extends AbstractThreadedPolyglotTest {

    @TruffleLanguage.Registration
    static class NotifyThreadEndedTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            CountDownLatch threadBeforeEnterLatch = new CountDownLatch(1);
            CountDownLatch threadAfterLeaveLatch = new CountDownLatch(1);
            AtomicReference<Thread> spawnedThreadReference = new AtomicReference<>();
            spawnedThreadReference.set(env.newTruffleThreadBuilder(() -> {
            }).beforeEnter(() -> {
                if (Thread.currentThread() == spawnedThreadReference.get()) {
                    try {
                        threadBeforeEnterLatch.await();
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                }
            }).afterLeave(() -> {
                if (Thread.currentThread() == spawnedThreadReference.get()) {
                    threadAfterLeaveLatch.countDown();
                }
            }).virtual(vthreads).build());
            spawnedThreadReference.get().start();
            env.getContext().leaveAndEnter(node, TruffleSafepoint.Interrupter.THREAD_INTERRUPT, (x) -> {
                threadBeforeEnterLatch.countDown();
                try {
                    threadAfterLeaveLatch.await();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                return null;
            }, null);
            spawnedThreadReference.get().join();
            return null;
        }

        @Override
        protected void initializeMultiThreading(ExecutableContext context) {
            Assert.fail("Multi threading should not be initialized");
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testAvoidMultiThreadingByNotifications() {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, NotifyThreadEndedTestLanguage.class, "");
        }
    }

    @TruffleLanguage.Registration
    static class CancelThreadBlockedInLeaveAndEnterTestLanguage extends TruffleLanguage<CancelThreadBlockedInLeaveAndEnterTestLanguage.Context> {
        static final String ID = TestUtils.getDefaultLanguageId(CancelThreadBlockedInLeaveAndEnterTestLanguage.class);

        static final Object WAIT_OBJECT = new Object();

        static class Context {
            private final Env env;
            private volatile boolean multiThreadingTriggered;
            private volatile int blockedThreadUnblocked;
            private volatile int interrupterResets;
            private final TruffleSafepoint.Interrupter interrupter = new TruffleSafepoint.Interrupter() {
                @Override
                public void interrupt(Thread thread) {
                    thread.interrupt();
                }

                @Override
                public void resetInterrupted() {
                    interrupterResets++;
                    Thread.interrupted();
                }
            };

            Context(Env env) {
                this.env = env;
            }

            private static final ContextReference<Context> REFERENCE = ContextReference.create(CancelThreadBlockedInLeaveAndEnterTestLanguage.class);
        }

        @Override
        protected Context createContext(Env env) {
            return new Context(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return evalBoundary(frame.getArguments());
                }

                @TruffleBoundary
                private Object evalBoundary(Object[] arguments) {
                    Context ctx = Context.REFERENCE.get(this);
                    ctx.env.getContext().leaveAndEnter(null, ctx.interrupter, (x) -> {
                        for (int i = 0; i < 10; i++) {
                            CountDownLatch otherThreadInLeaveAndEnter = new CountDownLatch(1);
                            Thread t = ctx.env.newTruffleThreadBuilder(() -> {
                                ctx.env.getContext().leaveAndEnter(null, ctx.interrupter, (y) -> {
                                    otherThreadInLeaveAndEnter.countDown();
                                    try {
                                        while (true) {
                                            synchronized (WAIT_OBJECT) {
                                                WAIT_OBJECT.wait();
                                            }
                                        }
                                    } finally {
                                        ctx.blockedThreadUnblocked++;
                                    }
                                }, null);
                                dontReachThisOrGetStuck();
                            }).virtual(vthreads).build();
                            Thread.UncaughtExceptionHandler originalHandler = t.getUncaughtExceptionHandler();
                            t.setUncaughtExceptionHandler((t1, e) -> {
                                if (e instanceof ThreadDeath || e.getMessage().endsWith("Execution got interrupted.")) {
                                    // ignore
                                } else {
                                    originalHandler.uncaughtException(t1, e);
                                }
                            });
                            t.start();
                            otherThreadInLeaveAndEnter.await();
                        }
                        return null;
                    }, null);
                    if (arguments.length > 0) {
                        try {
                            InteropLibrary.getUncached().execute(arguments[0]);
                        } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
                            throw new AssertionError(e);
                        }
                    }
                    return NullObject.SINGLETON;
                }
            }.getCallTarget();
        }

        @TruffleBoundary
        private static void dontReachThisOrGetStuck() {
            try {
                while (true) {
                    synchronized (WAIT_OBJECT) {
                        WAIT_OBJECT.wait();
                    }
                }
            } catch (InterruptedException ie) {
                throw new AssertionError(ie);
            }
        }

        @Override
        protected void initializeMultiThreading(Context context) {
            context.multiThreadingTriggered = true;
            Assert.fail("Multi threading should not be initialized");
        }

        @Override
        protected void finalizeContext(Context context) {
            Assert.assertEquals(10, context.blockedThreadUnblocked);
            Assert.assertEquals(10, context.interrupterResets);
            Assert.assertFalse(context.multiThreadingTriggered);
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testCancelThreadBlockedInLeaveAndEnter() {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.eval(CancelThreadBlockedInLeaveAndEnterTestLanguage.ID, "");
            context.close(true);
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testInterruptThreadBlockedInLeaveAndEnter() throws TimeoutException {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.eval(CancelThreadBlockedInLeaveAndEnterTestLanguage.ID, "");
            context.interrupt(Duration.ZERO);
        }
    }

    @Test
    public void testCancelThreadBlockedInLeaveAndEnterFromEnteredThread() {
        try (Context context = Context.newBuilder().allowCreateThread(true).allowHostAccess(HostAccess.ALL).build()) {
            Value executable = context.parse(CancelThreadBlockedInLeaveAndEnterTestLanguage.ID, "");
            executable.execute(new Runnable() {
                @Override
                public void run() {
                    context.close(true);
                }
            });
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @TruffleLanguage.Registration
    static class ThreadNotificationsIllegalOperationsTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try (ByteArrayOutputStream errorBytes = new ByteArrayOutputStream(); PrintStream errors = new PrintStream(errorBytes)) {
                AtomicReference<Thread> spawnedThreadReference = new AtomicReference<>();
                spawnedThreadReference.set(env.newTruffleThreadBuilder(() -> {
                }).beforeEnter(() -> {
                    if (Thread.currentThread() == spawnedThreadReference.get()) {
                        try {
                            env.getContext().enter(null);
                        } catch (Throwable e) {
                            if (!(e instanceof IllegalStateException) || !"Context cannot be entered in polyglot thread's beforeEnter or afterLeave notifications.".equals(e.getMessage())) {
                                e.printStackTrace(errors);
                            } else {
                                errors.print("1");
                            }
                        }
                        try {
                            env.getContext().leave(null, null);
                        } catch (Throwable e) {
                            /*
                             * Leave doesn't get to throw the IllegalStateException because leaving
                             * an already left context fails on an earlier assert, if assertions are
                             * enabled.
                             */
                            if (!(e instanceof AssertionError) || !"Assert stack is empty.".equals(e.getMessage())) {
                                e.printStackTrace(errors);
                            } else {
                                errors.print("2");
                            }
                        }
                    }
                }).afterLeave(() -> {
                    if (Thread.currentThread() == spawnedThreadReference.get()) {
                        try {
                            env.getContext().enter(null);
                        } catch (Throwable e) {
                            if (!(e instanceof IllegalStateException) || !"Context cannot be entered in polyglot thread's beforeEnter or afterLeave notifications.".equals(e.getMessage())) {
                                e.printStackTrace(errors);
                            } else {
                                errors.print("3");
                            }
                        }
                        try {
                            env.getContext().leave(null, null);
                        } catch (Throwable e) {
                            /*
                             * Leave doesn't get to throw the IllegalStateException because leaving
                             * an already left context fails on an earlier assert, if assertions are
                             * enabled.
                             */
                            if (!(e instanceof AssertionError) || !"Assert stack is empty.".equals(e.getMessage())) {
                                e.printStackTrace(errors);
                            } else {
                                errors.print("4");
                            }
                        }
                    }

                }).virtual(vthreads).build());
                spawnedThreadReference.get().start();
                spawnedThreadReference.get().join();
                Assert.assertEquals("1234", errorBytes.toString());
            }
            return null;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testIllegalOperationsInThreadNotifications() {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, ThreadNotificationsIllegalOperationsTestLanguage.class, "");
        }
    }

    static Thread blockedPolyglotThread;

    @TruffleLanguage.Registration
    static class ThreadNotificationsCanPreventContextCloseTestLanguage extends TruffleLanguage<ThreadNotificationsCanPreventContextCloseTestLanguage.Context> {
        static final String ID = TestUtils.getDefaultLanguageId(ThreadNotificationsCanPreventContextCloseTestLanguage.class);

        static class Context {
            private final Env env;
            private final CountDownLatch threadBeforeEnterLatch = new CountDownLatch(1);
            private final CountDownLatch threadAfterLeaveLatch = new CountDownLatch(1);
            private final AtomicInteger finalizeContextCounter = new AtomicInteger();

            Context(Env env) {
                this.env = env;
            }

            private static final ContextReference<Context> REFERENCE = ContextReference.create(ThreadNotificationsCanPreventContextCloseTestLanguage.class);
        }

        @Override
        protected Context createContext(Env env) {
            return new Context(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return evalBoundary();
                }

                @SuppressWarnings("SynchronizeOnNonFinalField")
                @TruffleBoundary
                private Object evalBoundary() {
                    Context ctx = Context.REFERENCE.get(this);
                    int cnt = ctx.finalizeContextCounter.incrementAndGet();
                    if (cnt == 1) {
                        blockedPolyglotThread = ctx.env.newTruffleThreadBuilder(() -> {
                        }).beforeEnter(() -> {
                            if (Thread.currentThread() == blockedPolyglotThread) {
                                synchronized (blockedPolyglotThread) {
                                    ctx.threadBeforeEnterLatch.countDown();
                                    try {
                                        blockedPolyglotThread.wait();
                                    } catch (InterruptedException e) {
                                        throw new AssertionError(e);
                                    }
                                }
                            }
                        }).afterLeave(() -> {
                            if (Thread.currentThread() == blockedPolyglotThread) {
                                synchronized (blockedPolyglotThread) {
                                    ctx.threadAfterLeaveLatch.countDown();
                                    try {
                                        blockedPolyglotThread.wait();
                                    } catch (InterruptedException e) {
                                        throw new AssertionError(e);
                                    }
                                }
                            }
                        }).virtual(vthreads).build();
                        blockedPolyglotThread.start();
                    }
                    try {
                        ctx.threadBeforeEnterLatch.await();
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                    if (cnt > 1) {
                        try {
                            ctx.threadAfterLeaveLatch.await();
                        } catch (InterruptedException e) {
                            throw new AssertionError(e);
                        }
                    }
                    if (cnt > 2) {
                        try {
                            blockedPolyglotThread.join();
                        } catch (InterruptedException e) {
                            throw new AssertionError(e);
                        }
                    }
                    return NullObject.SINGLETON;
                }

            }.getCallTarget();
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @TruffleLanguage.Registration
    static class UnblockThreadLangauge extends AbstractExecutableTestLanguage {

        @SuppressWarnings("SynchronizeOnNonFinalField")
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            synchronized (blockedPolyglotThread) {
                blockedPolyglotThread.notifyAll();
            }
            return null;
        }
    }

    @Test
    public void testThreadNotificationCanPreventContextClose() {
        try (Engine engine = Engine.create()) {
            Context ctx = Context.newBuilder().engine(engine).allowCreateThread(true).build();
            // spawn thread and block in before enter notification
            ctx.eval(ThreadNotificationsCanPreventContextCloseTestLanguage.ID, "");
            try {
                ctx.close();
                Assert.fail();
            } catch (PolyglotException pe) {
                if (!pe.getMessage().contains("The language did not complete all polyglot threads but should have")) {
                    throw pe;
                }
            }
            try (Context unblockContext = Context.newBuilder().engine(engine).build()) {
                // unblock the before enter notification of the spawned thread
                AbstractExecutableTestLanguage.evalTestLanguage(unblockContext, UnblockThreadLangauge.class, "");
            }
            // wait until the spawned thread is blocked in the after leave notification
            ctx.eval(ThreadNotificationsCanPreventContextCloseTestLanguage.ID, "");
            try {
                ctx.close();
                Assert.fail();
            } catch (PolyglotException pe) {
                if (!pe.getMessage().contains("The language did not complete all polyglot threads but should have")) {
                    throw pe;
                }
            }
            try (Context unblockContext = Context.newBuilder().engine(engine).build()) {
                // unblock the after leave notification of the spawned thread
                AbstractExecutableTestLanguage.evalTestLanguage(unblockContext, UnblockThreadLangauge.class, "");
            }
            // join the spaned thread
            ctx.eval(ThreadNotificationsCanPreventContextCloseTestLanguage.ID, "");
            // closing the context is now possible
            ctx.close();
        } finally {
            blockedPolyglotThread = null;
        }
    }

    @TruffleLanguage.Registration
    static class ThreadRunnableNotNullableTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            AbstractPolyglotTest.assertFails(() -> env.newTruffleThreadBuilder(null), NullPointerException.class);
            return null;
        }
    }

    @Test
    public void testThreadRunnableNotNullable() {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, ThreadRunnableNotNullableTestLanguage.class, "");
        }
    }

    @SuppressWarnings("serial")
    static class BeforeEnterException extends RuntimeException {

    }

    @SuppressWarnings("serial")
    static class AfterLeaveException extends RuntimeException {

    }

    @TruffleLanguage.Registration
    static class ThreadNotificationErrorTestLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String throwCommand = (String) contextArguments[0];
            Thread t = env.newTruffleThreadBuilder(() -> {
            }).beforeEnter(() -> {
                if ("throwBefore".equals(throwCommand)) {
                    throw new BeforeEnterException();
                }
            }).afterLeave(() -> {
                if ("throwAfter".equals(throwCommand)) {
                    throw new AfterLeaveException();
                }
            }).virtual(vthreads).build();
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            return null;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testThreadNotificationError() throws IOException {
        try (ByteArrayOutputStream errorOutput = new ByteArrayOutputStream(); Context ctx = Context.newBuilder().allowCreateThread(true).err(errorOutput).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, ThreadNotificationErrorTestLanguage.class, "1", "throwBefore");
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, ThreadNotificationErrorTestLanguage.class, "2", "throwAfter");
            Pattern pattern = Pattern.compile(
                            "(com\\.oracle\\.truffle\\.api\\.test\\.polyglot\\.PolyglotThreadNotificationsTest\\$BeforeEnterException|com\\.oracle\\.truffle\\.api\\.test\\.polyglot\\.PolyglotThreadNotificationsTest\\$AfterLeaveException)");
            Matcher beforeEnterErrorMatcher = pattern.matcher(errorOutput.toString());
            int beforeEnterErrorCount = 0;
            int afterLeaveErrorCount = 0;
            while (beforeEnterErrorMatcher.find()) {
                String errorName = beforeEnterErrorMatcher.group(1);
                if (BeforeEnterException.class.getName().equals(errorName)) {
                    beforeEnterErrorCount++;
                }
                if (AfterLeaveException.class.getName().equals(errorName)) {
                    afterLeaveErrorCount++;
                }
            }
            Assert.assertEquals(1, beforeEnterErrorCount);
            Assert.assertEquals(1, afterLeaveErrorCount);
        }
    }

    @TruffleLanguage.Registration
    static class FinalizeThreadNotificationLanguage extends TruffleLanguage<FinalizeThreadNotificationLanguage.Context> {
        static final String ID = TestUtils.getDefaultLanguageId(FinalizeThreadNotificationLanguage.class);

        static class Context {
            private final Env env;
            Map<Thread, CallTarget> threadDisposeCallTargetMap = Collections.synchronizedMap(new WeakHashMap<>());
            AtomicInteger finalizeThreadCount = new AtomicInteger();
            AtomicInteger disposeThreadCount = new AtomicInteger();
            List<Thread> polyglotThreads = new ArrayList<>();

            Context(Env env) {
                this.env = env;
            }

            private static final ContextReference<Context> REFERENCE = ContextReference.create(FinalizeThreadNotificationLanguage.class);
        }

        @Override
        protected Context createContext(Env env) {
            return new Context(env);
        }

        enum Action {
            START_THREADS,
            FINALIZE_THREAD
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            Action action = Action.valueOf(request.getSource().getCharacters().toString());
            return new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return evalBoundary();
                }

                @TruffleBoundary
                private Object evalBoundary() {
                    Context ctx = Context.REFERENCE.get(this);
                    if (action == Action.START_THREADS) {
                        CountDownLatch countDownLatch = new CountDownLatch(10);
                        for (int i = 0; i < 10; i++) {
                            ctx.polyglotThreads.add(ctx.env.newTruffleThreadBuilder(() -> countDown(countDownLatch)).virtual(vthreads).build());
                            ctx.polyglotThreads.get(ctx.polyglotThreads.size() - 1).start();
                        }
                        TruffleSafepoint.setBlockedThreadInterruptible(this, CountDownLatch::await, countDownLatch);
                    }
                    if (action == Action.FINALIZE_THREAD) {
                        ctx.finalizeThreadCount.incrementAndGet();
                    }
                    return NullObject.SINGLETON;
                }

                @TruffleBoundary
                private static void countDown(CountDownLatch countDownLatch) {
                    countDownLatch.countDown();
                }

            }.getCallTarget();
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected void initializeThread(Context context, Thread thread) {
            context.threadDisposeCallTargetMap.put(thread, new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return evalBoundary();
                }

                @TruffleBoundary
                private Object evalBoundary() {
                    Context ctx = Context.REFERENCE.get(this);
                    ctx.disposeThreadCount.incrementAndGet();
                    return NullObject.SINGLETON;
                }

            }.getCallTarget());
        }

        @Override
        protected void finalizeThread(Context context, Thread thread) {
            CallTarget finalizeThreadCalltarget = context.env.parseInternal(
                            Source.newBuilder(FinalizeThreadNotificationLanguage.ID, Action.FINALIZE_THREAD.name(), Action.FINALIZE_THREAD.name()).build());
            if (finalizeThreadCalltarget != null) {
                finalizeThreadCalltarget.call();
            }
            if (context.polyglotThreads.contains(thread)) {
                // initialize new language during finalization of polyglot threads
                context.env.parseInternal(Source.newBuilder(ThreadFinalizationCounterLanguage.ID, "", "dummy").build());
            }
        }

        @Override
        protected void disposeThread(Context context, Thread thread) {
            CallTarget disposeThreadCalltarget = context.threadDisposeCallTargetMap.get(thread);
            if (disposeThreadCalltarget != null) {
                disposeThreadCalltarget.call();
            }
        }

        @Override
        protected void finalizeContext(Context context) {
            for (Thread polyglotThread : context.polyglotThreads) {
                TruffleSafepoint.setBlockedThreadInterruptible(null, Thread::join, polyglotThread);
            }
        }

        @Override
        protected void disposeContext(Context context) {
            Assert.assertEquals(1 + context.polyglotThreads.size(), context.finalizeThreadCount.get());
            Assert.assertEquals(1 + context.polyglotThreads.size(), context.disposeThreadCount.get());
        }
    }

    @Test
    public void testFinalizeThreadNotification() {
        try (Context ctx = Context.newBuilder().allowCreateThread(true).build()) {
            ctx.eval(FinalizeThreadNotificationLanguage.ID, FinalizeThreadNotificationLanguage.Action.START_THREADS.name());
        }
    }

    @TruffleLanguage.Registration(internal = true)
    static class ThreadFinalizationCounterLanguage extends TruffleLanguage<ThreadFinalizationCounterLanguage.Context> {
        static final String ID = TestUtils.getDefaultLanguageId(ThreadFinalizationCounterLanguage.class);

        static class Context {
            private final AtomicInteger initializedThreads = new AtomicInteger();
            private final AtomicInteger finalizedThreads = new AtomicInteger();
            private final AtomicInteger disposedThreads = new AtomicInteger();
        }

        @Override
        protected Context createContext(Env env) {
            return new Context();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return NullObject.SINGLETON;
                }
            }.getCallTarget();
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected void initializeThread(Context context, Thread thread) {
            context.initializedThreads.incrementAndGet();
        }

        @Override
        protected void finalizeThread(Context context, Thread thread) {
            context.finalizedThreads.incrementAndGet();
        }

        @Override
        protected void disposeThread(Context context, Thread thread) {
            context.disposedThreads.incrementAndGet();
        }

        @Override
        protected void disposeContext(Context context) {
            Assert.assertEquals(context.initializedThreads.get(), context.finalizedThreads.get());
            Assert.assertEquals(context.finalizedThreads.get(), context.disposedThreads.get());
        }
    }

    @TruffleLanguage.Registration
    static class DisallowedOperationsInFinalizeThreadLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(DisallowedOperationsInFinalizeThreadLanguage.class);

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }

        @Override
        protected void finalizeThread(ExecutableContext context, Thread thread) {
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Thread t = context.env.newTruffleThreadBuilder(() -> {
            }).virtual(vthreads).build();
            t.setUncaughtExceptionHandler((t1, e) -> throwableRef.set(e));
            t.start();
            try {
                t.join();
            } catch (InterruptedException ie) {
                throw new AssertionError(ie);
            }
            Assert.assertTrue(throwableRef.get() instanceof IllegalStateException);
            Assert.assertEquals("The Context is already closed.", throwableRef.get().getMessage());
            AbstractPolyglotTest.assertFails(() -> context.env.parseInternal(
                            Source.newBuilder(ThreadFinalizationCounterLanguage.ID, "", "dummy").build()), IllegalStateException.class,
                            e -> Assert.assertEquals(
                                            String.format("Creation of context for language %s is no longer allowed. Language contexts are finalized when embedder threads are being finalized.",
                                                            ThreadFinalizationCounterLanguage.ID),
                                            e.getMessage()));
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testDisallowedOperationsInFinalizeThread() {
        try (Context ctx = Context.newBuilder(DisallowedOperationsInFinalizeThreadLanguage.ID, FinalizeThreadNotificationLanguage.ID).allowCreateThread(true).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, DisallowedOperationsInFinalizeThreadLanguage.class, "");
        }
    }

    @TruffleLanguage.Registration
    static class DeadlockLanguageInitializationTestLanguage extends TruffleLanguage<DeadlockLanguageInitializationTestLanguage.Context> {
        static final String ID = TestUtils.getDefaultLanguageId(DeadlockLanguageInitializationTestLanguage.class);

        static class Context {
            private final Env env;
            private final Thread creationThread;

            Context(Env env, Thread creationThread) {
                this.env = env;
                this.creationThread = creationThread;
            }

            private static final ContextReference<Context> REFERENCE = ContextReference.create(DeadlockLanguageInitializationTestLanguage.class);
        }

        @Override
        protected Context createContext(Env env) {
            return new Context(env, Thread.currentThread());
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return evalBoundary();
                }

                @TruffleBoundary
                private Object evalBoundary() {
                    Context ctx = Context.REFERENCE.get(this);
                    Thread t = ctx.env.newTruffleThreadBuilder(() -> {
                    }).virtual(vthreads).build();
                    t.start();
                    ctx.env.initializeLanguage(ctx.env.getInternalLanguages().get(DummyLanguage1.ID));
                    TruffleSafepoint.setBlockedThreadInterruptible(this, Thread::join, t);
                    return NullObject.SINGLETON;
                }

            }.getCallTarget();
        }

        @Override
        protected void initializeThread(Context context, Thread thread) {
            if (thread != context.creationThread) {
                context.env.initializeLanguage(context.env.getInternalLanguages().get(DummyLanguage1.ID));
            }
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @TruffleLanguage.Registration(internal = true)
    static class DummyLanguage1 extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(DummyLanguage1.class);

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }

        @Override
        protected void initializeThread(ExecutableContext context, Thread thread) {
            context.env.initializeLanguage(context.env.getInternalLanguages().get(DummyLanguage2.ID));
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @TruffleLanguage.Registration(internal = true)
    static class DummyLanguage2 extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(DummyLanguage2.class);

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testLanguageInitializationDeadlock() {
        // TODO GR-47643 too slow with isolates
        TruffleTestAssumptions.assumeWeakEncapsulation();

        for (int i = 0; i < 1000; i++) {
            try (Context ctx = Context.newBuilder().allowCreateThread(true).build()) {
                ctx.eval(DeadlockLanguageInitializationTestLanguage.ID, "");
            }
        }
    }

}
