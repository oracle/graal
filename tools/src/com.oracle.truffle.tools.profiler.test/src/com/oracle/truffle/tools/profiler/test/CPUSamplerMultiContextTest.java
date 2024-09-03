/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.StackTraceEntry;

public class CPUSamplerMultiContextTest {
    public static final String FIB = """
                    function fib(n) {
                      if (n < 3) {
                        return 1;
                      } else {
                        return fib(n - 1) + fib(n - 2);
                      }
                    }
                    function main() {
                      return fib;
                    }
                    """;

    public static final String FIB_15_PLUS = """
                    function fib15plus(n, remainder) {
                      if (n < 15) {
                        return remainder(n);
                      } else {
                        return fib15plus(n - 1, remainder) + fib15plus(n - 2, remainder);
                      }
                    }
                    function main() {
                      return fib15plus;
                    }
                    """;

    @Test
    public void testSamplerDoesNotKeepContexts() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Engine engine = Engine.newBuilder().out(out).option("cpusampler", "histogram").build()) {
            List<WeakReference<Context>> contextReferences = new ArrayList<>();
            for (int i = 0; i < 27; i++) {
                try (Context context = Context.newBuilder().engine(engine).build()) {
                    contextReferences.add(new WeakReference<>(context));
                    Source src = Source.newBuilder("sl", FIB, "fib.sl").build();
                    Value fib = context.eval(src);
                    fib.execute(29);
                }
            }
            GCUtils.assertGc("CPUSampler prevented collecting contexts", contextReferences);
        }
        Pattern pattern = Pattern.compile("Sampling Histogram. Recorded (\\d+) samples");
        Matcher matcher = pattern.matcher(out.toString());
        int histogramCount = 0;
        while (matcher.find()) {
            histogramCount++;
            Assert.assertTrue("Histogram no. " + histogramCount + " didn't contain any samples.", Integer.parseInt(matcher.group(1)) > 0);
        }
        Assert.assertEquals(27, histogramCount);
    }

    static class RootCounter {
        int fibCount;
        int fib15plusCount;
    }

    @Test
    public void testMultiThreadedAndMultiContextPerThread() throws InterruptedException, ExecutionException, IOException {
        try (Engine engine = Engine.create(); ExecutorService executorService = Executors.newFixedThreadPool(10)) {
            AtomicBoolean runFlag = new AtomicBoolean(true);
            CPUSampler sampler = CPUSampler.find(engine);
            int nThreads = 5;
            int nSamples = 5;
            Map<Thread, RootCounter> threads = new ConcurrentHashMap<>();
            List<Future<?>> futures = new ArrayList<>();
            CountDownLatch fibLatch = new CountDownLatch(nThreads);
            Source src1 = Source.newBuilder("sl", FIB_15_PLUS, "fib15plus.sl").build();
            Source src2 = Source.newBuilder("sl", FIB, "fib.sl").build();
            for (int i = 0; i < nThreads; i++) {
                futures.add(executorService.submit(() -> {
                    threads.putIfAbsent(Thread.currentThread(), new RootCounter());
                    AtomicBoolean countedDown = new AtomicBoolean();
                    while (runFlag.get()) {
                        try (Context context1 = Context.newBuilder().engine(engine).build(); Context context2 = Context.newBuilder().engine(engine).build()) {
                            Value fib15plus = context1.eval(src1);
                            Value fib = context2.eval(src2);
                            ProxyExecutable proxyExecutable = (n) -> {
                                if (countedDown.compareAndSet(false, true)) {
                                    fibLatch.countDown();
                                }
                                return fib.execute((Object[]) n);
                            };
                            Assert.assertEquals(514229, fib15plus.execute(29, proxyExecutable).asInt());
                        }
                    }
                }));
            }
            fibLatch.await();
            for (int i = 0; i < nSamples; i++) {
                Map<Thread, List<StackTraceEntry>> sample = sampler.takeSample();
                for (Map.Entry<Thread, List<StackTraceEntry>> sampleEntry : sample.entrySet()) {
                    RootCounter rootCounter = threads.get(sampleEntry.getKey());
                    for (StackTraceEntry stackTraceEntry : sampleEntry.getValue()) {
                        if ("fib".equals(stackTraceEntry.getRootName())) {
                            rootCounter.fibCount++;
                        }
                        if ("fib15plus".equals(stackTraceEntry.getRootName())) {
                            rootCounter.fib15plusCount++;
                        }
                    }
                }
            }
            runFlag.set(false);
            for (Future<?> future : futures) {
                future.get();
            }
            for (Map.Entry<Thread, RootCounter> threadEntry : threads.entrySet()) {
                Assert.assertTrue(nSamples + " samples should contain at least 1 occurrence of the fib root for each thread, but one thread contained only " + threadEntry.getValue().fibCount,
                                threadEntry.getValue().fibCount > 1);
                Assert.assertTrue(nSamples + " samples should contain at least 10 occurrences of the fib15plus root, but one thread contained only " + threadEntry.getValue().fib15plusCount,
                                threadEntry.getValue().fib15plusCount > 10);
            }
        }
    }
}
