/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class ForceCloseTest {

    @Rule public TestName testNameRule = new TestName();

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    /**
     * This example shows how harmful long running scripts can be cancelled. Cancelling the current
     * execution will render the context inconsistent and therefore unusable.
     */
    @Test
    public void testCancelMultipleTimes() throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(1);
        Semaphore semaphore = new Semaphore(0);
        Engine engine = Engine.create();
        for (int x = 0; x < 10; x++) {
            Context context = Context.newBuilder(InstrumentationTestLanguage.ID).engine(engine).build();
            context.eval(InstrumentationTestLanguage.ID, "EXPRESSION");

            // we submit a harmful infinite script to the executor
            Future<Value> future = service.submit(() -> {
                context.enter();
                semaphore.release();
                try {
                    return context.eval(Source.newBuilder(InstrumentationTestLanguage.ID, "LOOP(infinity, EXPRESSION)", "test").build());
                } finally {
                    context.leave();
                }
            });

            /*
             * Run for bit to potentially trigger compiles.
             */
            int waitTime = (int) (Math.random() * 100);
            if (waitTime > 0) {
                Thread.sleep(waitTime);
            }
            /*
             * Makes sure we are at least entered.
             */
            semaphore.acquireUninterruptibly();

            context.close(true);

            try {
                future.get();
            } catch (ExecutionException e) {
                PolyglotException pe = (PolyglotException) e.getCause();
                assertTrue(pe.isCancelled());
            }
        }
        engine.close();
        service.shutdown();
    }

}
