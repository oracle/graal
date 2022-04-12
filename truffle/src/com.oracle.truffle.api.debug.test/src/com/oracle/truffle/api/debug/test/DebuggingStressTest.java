/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;

public class DebuggingStressTest extends AbstractDebugTest {

    private final Source source = testSource("ROOT(\n" +
                    "  DEFINE(bar, ROOT(STATEMENT(EXPRESSION), STATEMENT)),\n" +
                    "  DEFINE(foo, ROOT(EXPRESSION, \n" +
                    "                   STATEMENT(CALL(bar)))),\n" +
                    "  STATEMENT,\n" +
                    "  LOOP(500, STATEMENT(CALL(foo)))\n" +
                    ")\n");

    /**
     * Test of close of a session in the middle of the execution.
     */
    @Test
    public void testSessionClose() throws InterruptedException, ExecutionException {
        ExecutorService evalDoneExec = Executors.newFixedThreadPool(1);

        for (long closeTime = 1;; closeTime *= 2) {
            Future<?> evalFuture;
            try (DebuggerSession session = startSession()) {
                assert session != null;
                startEval(source);
                evalFuture = evalDoneExec.submit(() -> expectDone());
                try {
                    evalFuture.get(closeTime, TimeUnit.NANOSECONDS);
                    // The evaluation has finished before the close time.
                    break;
                } catch (TimeoutException timeout) {
                    // closeTime has expired, we'll close the session
                }
            }
            // Wait for eval to really finish if we closed the session early
            evalFuture.get();
        }
        evalDoneExec.shutdown();
        evalDoneExec.awaitTermination(1, TimeUnit.DAYS);
    }

    /**
     * Test of close of a session with breakpoints in the middle of the execution. Tests GR-36625,
     * deadlock of Breakpoint and instrumentation synchronization.
     */
    @Test
    public void testSessionCloseWithBreakpoints() throws InterruptedException {
        int numLines = source.getLineCount();
        Breakpoint[] bp = new Breakpoint[numLines];
        for (int i = 0; i < numLines; i++) {
            bp[i] = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(i + 1).build();
        }

        Context context = Context.newBuilder().build();
        BlockingQueue<Source> evalQueue = new LinkedBlockingQueue<>();
        Semaphore evalSemaphore = new Semaphore(0);

        // Perform evaluation in this thread. Debugger runs in main thread to be able to close early
        Thread evalThread = new Thread(() -> {
            try {
                for (;;) {
                    Source s = evalQueue.take();
                    context.eval(s);
                    evalSemaphore.release();
                }
            } catch (InterruptedException ex) {
                // Done.
            }
        });
        evalThread.start();

        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);

        for (long closeTime = 1;; closeTime *= 2) {
            try (DebuggerSession session = debugger.startSession(event -> event.prepareContinue())) {
                for (Breakpoint b : bp) {
                    session.install(b);
                }
                evalQueue.put(source);
                boolean finishedBeforeClose = evalSemaphore.tryAcquire(closeTime, TimeUnit.NANOSECONDS);
                if (finishedBeforeClose) {
                    break;
                }
            }
            evalSemaphore.acquire();
        }
        evalThread.interrupt();
        evalThread.join();
    }

}
