/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.truffle.test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Regression test for a deadlock caused by not resetting the {@code RootNode#lock} field in
 * {@code RootNode#copy}.
 */
public class GR35581Test {
    private static final int TEST_REPETITIONS = 100;

    static final class DummyRootNode extends RootNode {
        private static final DummyRootNode INSTANCE = new DummyRootNode();

        protected DummyRootNode() {
            super(null);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments().length > 0 ? frame.getArguments()[0] : 42;
        }
    }

    abstract static class DSLNodeToTakeTheASTLockAndThenGIL extends Node {
        private final CountDownLatch rootNodeLockAcquired;
        private final CountDownLatch proceedToAcquireGIL;

        protected DSLNodeToTakeTheASTLockAndThenGIL(CountDownLatch rootNodeLockAcquired, CountDownLatch proceedToAcquireGIL) {
            this.rootNodeLockAcquired = rootNodeLockAcquired;
            this.proceedToAcquireGIL = proceedToAcquireGIL;
        }

        public abstract Object execute();

        protected Object runUnderASTLock() {
            // Runs this while holding the AST lock
            // @Cached does that at the time of writing, but just to be sure:
            return atomic(() -> {
                rootNodeLockAcquired.countDown();

                // Since directCallNode is not adopted any calls to Node.atomic in
                // cloneCallTarget will lock Truffle GIL
                await(proceedToAcquireGIL);
                Thread.yield();
                DirectCallNode directCallNode = Truffle.getRuntime().createDirectCallNode(DummyRootNode.INSTANCE.getCallTarget());
                directCallNode.cloneCallTarget();

                // This is contrived example, but imagine that this whole logica happens in a
                // constructor of some node that would be @Cached. The directCallNode would
                // be a @Child field, and it would not be adopted yet.
                return 42;
            });
        }

        @Specialization
        Object doIt(@Cached("runUnderASTLock()") Object cacheValue) {
            return cacheValue;
        }
    }

    // In the original bug, this RootNode was copied with a shallow copy of the AST lock. All the
    // copies of this RootNode therefore shared the same lock...
    static final class WronglyCopiedRootNode extends RootNode {
        private WronglyCopiedRootNode(CountDownLatch rootNodeLockAcquired, CountDownLatch proceedToAcquireGIL) {
            super(null);
            getLock(); // To make sure the lock is initialized
            childNode = GR35581TestFactory.DSLNodeToTakeTheASTLockAndThenGILNodeGen.create(rootNodeLockAcquired, proceedToAcquireGIL);
        }

        @Child DSLNodeToTakeTheASTLockAndThenGIL childNode;

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return childNode.execute();
        }
    }

    static final String LANGUAGE_ID = "GR35581";

    @TruffleLanguage.Registration(id = LANGUAGE_ID, name = LANGUAGE_ID, contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    public static class GR35581TestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        CountDownLatch rootNodeLockAcquired = new CountDownLatch(1);
        CountDownLatch proceedToAcquireGIL = new CountDownLatch(1);
        CallTarget cachedTarget;

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            CharSequence src = request.getSource().getCharacters();
            if (src.equals("execute")) {
                WronglyCopiedRootNode rootNode = new WronglyCopiedRootNode(rootNodeLockAcquired, proceedToAcquireGIL);
                cachedTarget = rootNode.getCallTarget();
                return cachedTarget;
            } else if (src.equals("cloneCallTarget")) {
                rootNodeLockAcquired.await();
                // Now the other thread holds AST lock of rootNode, and is about to acquire the GIL
                // We will send it the signal to go and try to acquire the GIL, but with some delay,
                // so that this thread can now...
                DirectCallNode directCallNode = Truffle.getRuntime().createDirectCallNode(cachedTarget);
                // ...acquire the GIL lock, because directCallNode is not adopted, and then
                // it'll try to acquire the AST lock of rootNode, which is now shared between the
                // rootNode and its copy (in the bug case, should be a new different lock now).
                directCallNode.cloneCallTarget();
                return new DummyRootNode().getCallTarget(); // dummy value
            } else {
                await(rootNodeLockAcquired);
                sleep();
                proceedToAcquireGIL.countDown();
                return new DummyRootNode().getCallTarget(); // dummy value
            }
        }
    }

    @Test
    public void testRaceConditionWithDirectCallNodeSplit() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        try {
            for (int i = 0; i < TEST_REPETITIONS; i++) {
                try (Context ctx = Context.create(LANGUAGE_ID)) {
                    Future<Object> task1 = executorService.submit(() -> ctx.eval(LANGUAGE_ID, "execute"));
                    Future<Object> task2 = executorService.submit(() -> ctx.eval(LANGUAGE_ID, "cloneCallTarget"));
                    Future<Object> task3 = executorService.submit(() -> ctx.eval(LANGUAGE_ID, "synchronizer"));
                    try {
                        task1.get(10, TimeUnit.SECONDS);
                        task2.get(10, TimeUnit.SECONDS);
                        task3.get(10, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        throw new AssertionError("The test seems to deadlock");
                    }
                }
            }
        } finally {
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException ignored) {
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
