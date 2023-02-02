/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class MultiThreadingTest extends AbstractPolyglotTest {
    private static final Node INVALID_NODE = new Node() {
    };

    public MultiThreadingTest() {
        needsLanguageEnv = true;
    }

    @Test
    public void testInitMultiThreading() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            int multiThreadingInits = 0;
            int lastIterationNo = 0;
            AtomicBoolean multiThreadingInitNotExecuted = new AtomicBoolean();
            for (int itNo = 0; itNo < 10000 && !multiThreadingInitNotExecuted.get(); itNo++) {
                lastIterationNo = itNo;
                CountDownLatch enterLeaveLoopLatch = new CountDownLatch(1);
                enterContext = false;
                AtomicBoolean testEnd = new AtomicBoolean();
                AtomicBoolean multiThreadingInitialized = new AtomicBoolean();
                setupEnv(Context.newBuilder(), new ProxyLanguage() {
                    @Override
                    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                        return true;
                    }

                    @Override
                    protected void initializeMultiThreading(LanguageContext c) {
                        multiThreadingInitialized.set(true);
                    }
                });
                AtomicInteger enteredCount = new AtomicInteger();
                Future<?> future1 = executorService.submit(() -> {
                    TruffleContext truffleContext = languageEnv.getContext();
                    Object prev = truffleContext.enter(INVALID_NODE);
                    truffleContext.leave(INVALID_NODE, prev);
                    enterLeaveLoopLatch.countDown();
                    while (!testEnd.get()) {
                        prev = truffleContext.enter(INVALID_NODE);
                        int localEnteredCount = enteredCount.incrementAndGet();
                        if (localEnteredCount > 1 && !multiThreadingInitialized.get()) {
                            multiThreadingInitNotExecuted.set(true);
                        }
                        enteredCount.decrementAndGet();
                        truffleContext.leave(INVALID_NODE, prev);
                    }
                });
                Future<?> future2 = executorService.submit(() -> {
                    TruffleContext truffleContext = languageEnv.getContext();
                    try {
                        enterLeaveLoopLatch.await();
                    } catch (InterruptedException ie) {
                        throw new AssertionError(ie);
                    }
                    Object prev = truffleContext.enter(INVALID_NODE);
                    int localEnteredCount = enteredCount.incrementAndGet();
                    if (localEnteredCount > 1 && !multiThreadingInitialized.get()) {
                        multiThreadingInitNotExecuted.set(true);
                    }
                    enteredCount.decrementAndGet();
                    truffleContext.leave(INVALID_NODE, prev);
                    testEnd.set(true);
                });
                enterLeaveLoopLatch.await();
                future1.get();
                future2.get();
                context.close();
                if (multiThreadingInitialized.get()) {
                    multiThreadingInits++;
                }
            }
            Assert.assertTrue("Multi-threading was never initialized!", multiThreadingInits > 0);
            Assert.assertFalse("Multi-threading was not initialized even though more than one thread was entered at the same time! lastIterationNo = " + lastIterationNo,
                            multiThreadingInitNotExecuted.get());
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }
}
