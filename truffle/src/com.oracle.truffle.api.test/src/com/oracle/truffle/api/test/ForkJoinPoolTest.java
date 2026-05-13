/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.junit.Assume;
import org.junit.Test;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ForkJoinPoolTest {

    /**
     * ThreadLocal marker used to verify that the common-pool worker cleared ordinary
     * thread-local state between top-level tasks.
     */
    private static final ThreadLocal<Boolean> MARKER = new ThreadLocal<>();

    @Test
    public void testForkJoinPool() throws Exception {
        Assume.assumeFalse("SubprocessTestUtils is not yet supported in native-image", ImageInfo.inImageCode());
        /*
         * This test requires common-pool workers that clear ordinary ThreadLocals between
         * top-level tasks. That is the default behavior on JDK 18 and earlier, and again
         * on JDK 24 and later. On JDK 19 through 23, the common pool preserves
         * ThreadLocals by default when no SecurityManager is installed, so the test cannot
         * exercise the intended scenario.
         */
        int jdkFeatureVersion = Runtime.version().feature();
        Assume.assumeTrue(jdkFeatureVersion <= 18 || jdkFeatureVersion >= 24);
        /*
         * The JDK common pool uses innocuous worker threads, which may clear ThreadLocal state
         * between top-level tasks. Workers in user-created ForkJoinPool instances preserve
         * ThreadLocals by default. JDK 19 added a
         * ForkJoinWorkerThread constructor for selecting the clearing policy explicitly, but this
         * test must remain JDK 17 source/API compatible. Run the test in a subprocess and set
         * the common-pool parallelism to one, so both submissions execute deterministically on the
         * same common-pool worker.
         */
        SubprocessTestUtils.newBuilder(ForkJoinPoolTest.class, () -> {
            try (Context context = Context.newBuilder().build()) {
                AtomicReference<Thread> worker = new AtomicReference<>();
                ForkJoinTask<Boolean> first = ForkJoinPool.commonPool().submit(() -> {
                    worker.set(Thread.currentThread());
                    MARKER.set(Boolean.TRUE);
                    return TestForkJoinPool.executeInContext(context);
                });
                assertTrue(first.get(10, TimeUnit.SECONDS));
                /*
                 * first.get() only guarantees that the first task completed. It does not guarantee
                 * that the common-pool worker has gone idle and executed its ThreadLocal clearing
                 * path. Wait until the worker reaches an idle/blocking state before submitting the
                 * second task.
                 */
                awaitWorkerIdle(worker.get());

                ForkJoinTask<Boolean> second = ForkJoinPool.commonPool().submit(() -> {
                    assertSame(worker.get(), Thread.currentThread());
                    assertNull(MARKER.get());
                    return TestForkJoinPool.executeInContext(context);
                });
                assertTrue(second.get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }).//
                        prefixVmOption("-Djava.util.concurrent.ForkJoinPool.common.parallelism=1").//
                        run();
    }

    /**
     * Waits until the worker thread has no immediately available top-level task and reaches a parked
     * state. For common-pool innocuous workers, this transition is the point at which ThreadLocal
     * state is cleared before the worker is reactivated for subsequent work.
     */
    private static void awaitWorkerIdle(Thread worker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Thread.State state = worker.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("ForkJoin worker did not become idle");
    }

    @TruffleLanguage.Registration
    public static final class TestForkJoinPool extends AbstractExecutableTestLanguage {

        static Boolean executeInContext(Context context) {
            return evalTestLanguage(context, TestForkJoinPool.class, "").asBoolean();
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleSafepoint.getCurrent();
            return true;
        }
    }
}
