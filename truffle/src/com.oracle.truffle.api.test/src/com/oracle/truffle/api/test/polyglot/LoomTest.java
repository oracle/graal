/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.TruffleOptions;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoomTest extends AbstractPolyglotTest {

    @Test
    public void testEmbedderVirtualThread() throws Throwable {
        Assume.assumeTrue(canCreateVirtualThreads());

        var log = new ByteArrayOutputStream();
        AbstractPolyglotTest.runInVirtualThread(() -> {
            try (Context ctx = Context.newBuilder().logHandler(log).allowExperimentalOptions(true).option("engine.WarnVirtualThreadSupport", "true").build()) {
                Value value = ctx.eval("sl", "function main() { return 42; }");
                assertEquals(42, value.asInt());
            }
        });

        if (isGraalRuntime()) {
            assertTrue(log.toString(), log.toString().startsWith("[engine] WARNING: Using polyglot contexts on Java virtual threads"));
        } else {
            assertEquals("", log.toString());
        }
    }

    @Ignore("GR-54696 too slow and can hang")
    @Test
    public void testManyVirtualThreads() throws Throwable {
        Assume.assumeTrue(canCreateVirtualThreads() && !TruffleOptions.AOT);
        Assume.assumeTrue("This test is too slow on non-Linux in CI. On Linux it takes about 7s locally.", System.getProperty("os.name").contains("Linux"));

        // Above 65535, which is the documented limit for Phaser, used by
        // ThreadLocalHandshake.Handshake
        int n = 66000;

        try (Context ctx = Context.create()) {
            Throwable[] error = new Throwable[1];
            Thread[] threads = new Thread[n];
            CountDownLatch waitStarted = new CountDownLatch(n);
            AtomicBoolean leave = new AtomicBoolean(false);

            for (int i = 0; i < n; i++) {
                threads[i] = Thread.startVirtualThread(() -> {
                    ctx.enter();
                    try {
                        waitStarted.countDown();
                        while (true) {
                            ctx.safepoint();
                            if (leave.get()) {
                                break;
                            }
                            Thread.yield();
                        }
                    } catch (Throwable t) {
                        if (!t.getMessage().equals("Execution got interrupted.")) {
                            t.printStackTrace();
                            error[0] = t;
                        }
                    } finally {
                        ctx.leave();
                    }
                });
            }

            waitStarted.await();
            assertFails(() -> {
                try {
                    ctx.interrupt(Duration.ZERO);
                } catch (TimeoutException e) {
                    throw new Error(e);
                }
            }, PolyglotException.class, e -> {
                assertEquals("java.lang.UnsupportedOperationException: Truffle does not currently support more than 65535 threads concurrently entered in the same context due to Phaser limitations",
                                e.getMessage());
            });

            leave.set(true);
            for (Thread thread : threads) {
                thread.join();
            }
            if (error[0] != null) {
                throw error[0];
            }
        }
    }

}
