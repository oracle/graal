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
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Context;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MultiThreadedCloseTest extends AbstractPolyglotTest {

    public MultiThreadedCloseTest() {
        enterContext = false;
    }

    @Test
    public void testWithExecutor() {
        setupEnv(Context.newBuilder().allowCreateThread(true).build(), new CloseLanguage() {

            @Override
            protected void initializeContext(CloseContext ctx) throws Exception {
                ctx.executor = Executors.newCachedThreadPool((r) -> {
                    return ctx.registerThread(ctx.env.createThread(r, null, ctx.group));
                });
                ctx.executor.submit(() -> {
                    ctx.env.parseInternal(Source.newBuilder(ProxyLanguage.ID, "", "test").build());
                });
            }

            @Override
            protected void finalizeContext(CloseContext ctx) {
                ctx.executor.shutdownNow();
                assertTrue(ctx.executor.isShutdown());
                try {
                    ctx.joinThreads();
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
        });
        context.eval(ProxyLanguage.ID, "");
    }

    @Test
    public void testWithThreads() {
        setupEnv(Context.newBuilder().allowCreateThread(true).build(), new CloseLanguage() {

            @Override
            protected void initializeContext(CloseContext ctx) throws Exception {
                ctx.registerThread(ctx.env.createThread(() -> {
                    ctx.env.parseInternal(Source.newBuilder(ProxyLanguage.ID, "", "test").build());
                }, null, ctx.group)).start();
            }

            @Override
            protected void finalizeContext(CloseContext ctx) {
                try {
                    ctx.joinThreads();
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
        });
        context.eval(ProxyLanguage.ID, "");
    }

    private static class CloseContext extends ProxyLanguage.LanguageContext {

        final AtomicReference<Collection<Thread>> createdThreads;
        final ThreadGroup group;
        ExecutorService executor;

        CloseContext(Env env) {
            super(env);
            this.createdThreads = new AtomicReference<>(Collections.synchronizedList(new ArrayList<>()));
            this.group = new ThreadGroup(getClass().getSimpleName());
        }

        Thread registerThread(Thread thread) {
            Collection<Thread> into = createdThreads.get();
            if (into == null) {
                throw new IllegalStateException("Creating a Thread after finalizeContext");
            }
            into.add(thread);
            return thread;
        }

        void joinThreads() throws InterruptedException {
            Collection<Thread> toJoin = createdThreads.getAndSet(null);
            if (toJoin == null) {
                return;
            }
            for (Thread t : toJoin) {
                t.join();
            }
        }
    }

    private abstract static class CloseLanguage extends ProxyLanguage {

        @Override
        protected final boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected LanguageContext createContext(Env env) {
            super.createContext(env);
            return new CloseContext(env);
        }

        @Override
        protected final void initializeContext(LanguageContext context) throws Exception {
            initializeContext((CloseContext) context);
        }

        protected abstract void initializeContext(CloseContext context) throws Exception;

        @Override
        protected void finalizeContext(LanguageContext context) {
            finalizeContext((CloseContext) context);
        }

        protected abstract void finalizeContext(CloseContext context);

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
        }
    }
}
