/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public final class InstrumentSystemThreadTest extends AbstractInstrumentationTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    public InstrumentSystemThreadTest() {
        needsInstrumentEnv = true;
        needsLanguageEnv = true;
    }

    @Test
    public void testCreateSystemThread() throws InterruptedException {
        AtomicIntegerArray res = new AtomicIntegerArray(2);
        Thread t1 = instrumentEnv.createSystemThread(() -> res.set(0, 1));
        Thread t2 = instrumentEnv.createSystemThread(() -> res.set(1, 1));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        Assert.assertEquals(1, res.get(0));
        Assert.assertEquals(1, res.get(1));
    }

    @Test
    public void testNonTerminatedSystemThread() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        Thread t1 = instrumentEnv.createSystemThread(() -> {
            started.countDown();
            try {
                closed.await();
            } catch (InterruptedException ie) {
                throw new AssertionError("Interrupted", ie);
            }
        });
        t1.setName("Forgotten thread");
        t1.start();
        try {
            started.await();
            assertFails((Runnable) engine::close, PolyglotException.class, (pe) -> {
                Assert.assertTrue(pe.isInternalError());
                String message = pe.getMessage();
                Assert.assertTrue(message, message.startsWith("java.lang.IllegalStateException: Alive system thread 'Forgotten thread'"));
                // There is a system thread dump:
                Assert.assertTrue(message, message.contains("com.oracle.truffle.polyglot.SystemThread.run"));
                Assert.assertTrue(message, message.endsWith(String.format("The engine has an alive system thread 'Forgotten thread' created by instrument %s.", ProxyInstrument.ID)));
            });
        } finally {
            closed.countDown();
        }
    }

    @Test
    public void testCreateSystemThreadAfterDispose() {
        engine.close();
        assertFails(() -> instrumentEnv.createSystemThread(() -> {
        }), IllegalStateException.class, (ise) -> {
            Assert.assertEquals(String.format("Engine is already closed. Cannot start a new system thread for instrument %s.", ProxyInstrument.ID), ise.getMessage());
        });
    }

    @Test
    public void testStartSystemThreadAfterDispose() throws InterruptedException {
        Thread thread = instrumentEnv.createSystemThread(() -> {
        });
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>();
        thread.setUncaughtExceptionHandler((t, e) -> {
            exceptionRef.set(e);
        });
        engine.close();
        thread.start();
        thread.join();
        Throwable exception = exceptionRef.get();
        Assert.assertTrue(exception instanceof IllegalStateException);
        Assert.assertEquals(String.format("Engine is already closed. Cannot start a new system thread for instrument %s.", ProxyInstrument.ID), exception.getMessage());
    }

    @Test
    public void testEnterInSystemThread() throws InterruptedException {
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>();
        TruffleContext innerContext = languageEnv.newInnerContextBuilder().initializeCreatorContext(true).inheritAllAccess(true).build();
        Thread thread = instrumentEnv.createSystemThread(() -> {
            try {
                innerContext.enter(null);
                Assert.fail("Should not reach here");
            } catch (Throwable t) {
                exceptionRef.set(t);
            }
        });
        thread.start();
        thread.join();
        Throwable exception = exceptionRef.get();
        Assert.assertTrue(exception instanceof IllegalStateException);
        Assert.assertEquals("Context cannot be entered on system threads.", exception.getMessage());
    }
}
