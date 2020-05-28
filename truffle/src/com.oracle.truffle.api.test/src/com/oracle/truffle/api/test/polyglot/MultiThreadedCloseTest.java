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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MultiThreadedCloseTest {

    @Test
    public void testWithExecutor() {
        ProxyLanguage.setDelegate(new CloseLanguage());
        CloseLanguage.contextFactory = ExecutorCloseContext::new;
        try (Context ctx = Context.newBuilder().allowCreateThread(true).build()) {
            ctx.eval(ProxyLanguage.ID, "");
        }
    }

    @Test
    public void testWithThreads() {
        ProxyLanguage.setDelegate(new CloseLanguage());
        CloseLanguage.contextFactory = ThreadCloseContext::new;
        try (Context ctx = Context.newBuilder().allowCreateThread(true).build()) {
            ctx.eval(ProxyLanguage.ID, "");
        }
    }

    private abstract static class CloseContext extends ProxyLanguage.LanguageContext {

        private final AtomicReference<Collection<Thread>> createdThreads;
        final ThreadGroup group;

        CloseContext(Env env) {
            super(env);
            this.createdThreads = new AtomicReference<>(Collections.synchronizedList(new ArrayList<>()));
            this.group = new ThreadGroup(getClass().getSimpleName());
        }

        abstract void init();

        abstract void cleanUp();

        final Thread onThreadCreate(Thread thread) {
            Collection<Thread> into = createdThreads.get();
            if (into == null) {
                throw new IllegalStateException("Creating a Thread after finalizeContext");
            }
            into.add(thread);
            return thread;
        }

        final void joinThreads() throws InterruptedException {
            Collection<Thread> toJoin = createdThreads.getAndSet(null);
            if (toJoin == null) {
                return;
            }
            for (Thread t : toJoin) {
                t.join();
            }
        }
    }

    private static final class ExecutorCloseContext extends CloseContext implements ThreadFactory {

        private final ExecutorService executor;

        ExecutorCloseContext(Env env) {
            super(env);
            this.executor = Executors.newCachedThreadPool(this);
        }

        @Override
        public Thread newThread(Runnable r) {
            return onThreadCreate(env.createThread(r, null, group));
        }

        @Override
        void init() {
            executor.submit(() -> {
                env.parseInternal(Source.newBuilder(ProxyLanguage.ID, "", "test").build());
            });
        }

        @Override
        void cleanUp() {
            executor.shutdownNow();
            assertTrue(executor.isShutdown());
            try {
                // assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
                joinThreads();
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        }
    }

    private static final class ThreadCloseContext extends CloseContext {

        ThreadCloseContext(Env env) {
            super(env);
        }

        @Override
        void init() {
            onThreadCreate(env.createThread(() -> {
                env.parseInternal(Source.newBuilder(ProxyLanguage.ID, "", "test").build());
            }, null, group)).start();
        }

        @Override
        void cleanUp() {
            try {
                joinThreads();
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        }
    }

    private static final class CloseLanguage extends ProxyLanguage {

        static Function<Env, CloseContext> contextFactory;

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected LanguageContext createContext(Env env) {
            return contextFactory.apply(env);
        }

        @Override
        protected void initializeContext(LanguageContext context) throws Exception {
            ((CloseContext) context).init();
        }

        @Override
        protected void finalizeContext(LanguageContext context) {
            ((CloseContext) context).cleanUp();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
        }
    }
}
