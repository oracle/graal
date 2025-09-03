/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.tck.TruffleRunner;

// don't extend from NFI test to prevent eager loading of libraries
public class ThreadErrnoNFITest {

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule(Context.newBuilder().allowNativeAccess(true).allowIO(IOAccess.ALL));

    private Functions fn;

    final class Functions {

        private final Object getErrno;
        private final Object setErrno;

        private static final InteropLibrary INTEROP = InteropLibrary.getUncached();

        Functions() throws InteropException {
            Source libSource = NFITest.parseSource("load '" + NFITest.getLibPath("nativetest") + "'");
            Object library = runWithPolyglot.getTruffleTestEnv().parseInternal(libSource).call();

            getErrno = lookup(library, "getErrno", "():sint32");
            setErrno = lookup(library, "setErrno", "(sint32):void");
        }

        private static Object lookup(Object library, String name, String signature) throws InteropException {
            Object symbol = INTEROP.readMember(library, name);
            Source sigSource = NFITest.parseSource(signature);
            Object sig = runWithPolyglot.getTruffleTestEnv().parseInternal(sigSource).call();
            return SignatureLibrary.getUncached().bind(sig, symbol);
        }

        int getErrno() throws InteropException {
            return (int) INTEROP.execute(getErrno);
        }

        void setErrno(int e) throws InteropException {
            INTEROP.execute(setErrno, e);
        }
    }

    private static final int TIMEOUT_MILLIS = 10000;

    final class TestThreadRunnable implements Runnable {

        private final int errno;

        private final Object lock = new Object();
        private volatile boolean waiting;
        private volatile Runnable extraRunnable;

        Throwable error = null;

        TestThreadRunnable(int errno) {
            this.errno = errno;
        }

        /**
         * Wait until the thread hits the next syncpoint.
         */
        void ensureSyncpoint() throws Throwable {
            synchronized (lock) {
                long waitStart = System.currentTimeMillis();
                Throwable err = TruffleSafepoint.setBlockedThreadInterruptibleFunction(null, lockObject -> {
                    assert Thread.holdsLock(lockObject);
                    long currentTime;
                    while (error == null && !waiting && ((currentTime = System.currentTimeMillis()) - waitStart) < TIMEOUT_MILLIS) {
                        lockObject.wait(TIMEOUT_MILLIS - (currentTime - waitStart));
                    }
                    return error;
                }, lock);
                if (err != null) {
                    throw err;
                }
                if (!waiting) {
                    throw new TimeoutException();
                }
            }
        }

        /**
         * Continue running.
         */
        void cont() throws Throwable {
            cont(null);
        }

        /**
         * Continue running, and inject some extra work.
         */
        void cont(Runnable extra) throws Throwable {
            synchronized (lock) {
                ensureSyncpoint();
                waiting = false;
                extraRunnable = extra;
                lock.notifyAll();
            }
        }

        private void syncpoint() throws TimeoutException {
            synchronized (lock) {
                waiting = true;
                lock.notifyAll();
                long waitStart = System.currentTimeMillis();
                TruffleSafepoint.setBlockedThreadInterruptible(null, lockObject -> {
                    assert Thread.holdsLock(lockObject);
                    long currentTime;
                    while (waiting && ((currentTime = System.currentTimeMillis()) - waitStart) < TIMEOUT_MILLIS) {
                        lockObject.wait(TIMEOUT_MILLIS - (currentTime - waitStart));
                    }
                    if (!waiting && extraRunnable != null) {
                        extraRunnable.run();
                        extraRunnable = null;
                    }

                }, lock);
                if (waiting) {
                    throw new TimeoutException();
                }
            }
        }

        public void run() {
            try {
                runWithPolyglot.getPolyglotContext().enter();
                syncpoint(); // 1
                fn.setErrno(errno);
                syncpoint(); // 2
                int e = fn.getErrno();
                Assert.assertEquals("errno", errno, e);
            } catch (Throwable t) {
                synchronized (lock) {
                    error = t;
                    lock.notifyAll();
                }
            } finally {
                runWithPolyglot.getPolyglotContext().leave();
            }
        }
    }

    static final class ForceSafepointNode extends RootNode {

        ForceSafepointNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleSafepoint.pollHere(this);
            return null;
        }
    }

    private static final int NUM_THREADS = 4;
    private static final int EARLY_THREADS = 2;

    @Test
    public void testMultiThreadErrno() throws Throwable {
        TestThreadRunnable[] testThreadRunnable = new TestThreadRunnable[NUM_THREADS];
        for (int i = 0; i < NUM_THREADS; i++) {
            testThreadRunnable[i] = new TestThreadRunnable(i);
        }

        Thread[] threads = new Thread[NUM_THREADS];

        // start some threads early before the NFI is initialized
        for (int i = 0; i < EARLY_THREADS; i++) {
            threads[i] = new Thread(testThreadRunnable[i]);
            threads[i].start();
            // run to syncpoint 1 to ensure the thread is entered before we initialize NFI
            testThreadRunnable[i].ensureSyncpoint();
        }

        // initialize the NFI
        fn = new Functions();

        // start the other threads
        for (int i = EARLY_THREADS; i < NUM_THREADS; i++) {
            threads[i] = new Thread(testThreadRunnable[i]);
            threads[i].start();
        }

        CallTarget forceSafepoint = new ForceSafepointNode(runWithPolyglot.getTestLanguage()).getCallTarget();

        // for all threads:
        // continue past syncpoint 1, telling them to set their errno
        // then wait until syncpoint 2, i.e. their errno is set
        for (int i = 0; i < NUM_THREADS; i++) {
            Runnable r = null;
            if (i % 2 == 0) {
                // inject a safepoint in every even thread
                r = () -> {
                    forceSafepoint.call();
                };
            }
            testThreadRunnable[i].cont(r);
            testThreadRunnable[i].ensureSyncpoint();
        }

        // let all threads continue past syncpoint 2, and verify their errno is unmodified
        for (int i = 0; i < NUM_THREADS; i++) {
            testThreadRunnable[i].cont();
        }

        // wait for all threads to finish and verify there was no error
        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i].join(1000);
            if (testThreadRunnable[i].error != null) {
                throw testThreadRunnable[i].error;
            }
        }
    }
}
