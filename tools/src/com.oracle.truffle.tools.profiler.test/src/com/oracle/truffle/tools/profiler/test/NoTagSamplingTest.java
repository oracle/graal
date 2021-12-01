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
package com.oracle.truffle.tools.profiler.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.StackTraceEntry;

public class NoTagSamplingTest {

    private static final String TEST_ROOT_NAME = LILRootNode.class.getName();

    private static Semaphore await;

    @Test
    public void testNoTagSampling() throws InterruptedException, ExecutionException {
        ExecutorService singleThread = Executors.newSingleThreadExecutor();
        await = new Semaphore(0);
        try (Context context = Context.create(NoTagLanguage.ID)) {
            CPUSampler sampler = CPUSampler.find(context.getEngine());
            Source source = Source.newBuilder(NoTagLanguage.ID, "", "").buildLiteral();
            Future<?> f = singleThread.submit(() -> {
                return context.eval(source).asInt();
            });

            Map<Thread, List<StackTraceEntry>> sample = null;
            for (int i = 0; i < 10000; i++) { // times out after 10s
                sample = sampler.takeSample();
                if (!sample.isEmpty()) {
                    break;
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }

            // wait for future
            await.release();
            assertEquals(42, f.get());
            assertTrue(!sample.isEmpty());
            List<StackTraceEntry> entries = sample.values().iterator().next();
            assertEquals(1, entries.size());
            assertEquals(TEST_ROOT_NAME, entries.get(0).getRootName());

            singleThread.shutdown();
            singleThread.awaitTermination(10, TimeUnit.SECONDS);
        }

    }

    public static class LILRootNode extends RootNode {

        protected LILRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public String getName() {
            return TEST_ROOT_NAME;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleSafepoint.setBlockedThreadInterruptible(this, Semaphore::acquire, await);
            return 42;
        }
    }

    @TruffleLanguage.Registration(id = NoTagLanguage.ID, name = NoTagLanguage.ID, version = "0.0.1")
    public static class NoTagLanguage extends ProxyLanguage {
        static final String ID = "NoTagSamplingTest_NoTagLanguage";

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return newTarget();
        }

        private RootCallTarget newTarget() {
            return new LILRootNode(this).getCallTarget();
        }
    }
}
