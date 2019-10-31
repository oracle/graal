/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.truffle.api.test.polyglot.PolyglotCachingTest.ReuseLanguage;

public class ContextParallelCloseTest {

    @Test
    public void testCloseDeadlock() throws InterruptedException, ExecutionException {
        final int threads = 10;
        ExecutorService service = Executors.newFixedThreadPool(threads);
        try {
            for (int iteration = 0; iteration < 100; iteration++) {
                Engine engine;
                Context context;
                if (iteration % 2 == 0) {
                    engine = Engine.create();
                    context = Context.newBuilder().engine(engine).build();
                } else {
                    context = Context.newBuilder().build();
                    engine = context.getEngine();
                }
                context.initialize(ReuseLanguage.ID);
                final CountDownLatch latch = new CountDownLatch(threads);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    final int innerIteration = i;
                    futures.add(service.submit(() -> {
                        latch.countDown();
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                        }
                        if (innerIteration % 3 == 0) {
                            context.close();
                            engine.close();
                        } else {
                            engine.close();
                        }
                    }));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
                try {
                    // context must be closed successfully by at least one thread
                    context.eval(ReuseLanguage.ID, "");
                    fail();
                } catch (IllegalStateException e) {
                    assertEquals("The Context is already closed.", e.getMessage());
                }
            }
        } finally {
            service.shutdown();
        }
    }

}
