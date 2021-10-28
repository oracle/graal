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
package com.oracle.truffle.polyglot;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleSafepoint;

final class PauseThreadLocalAction extends ThreadLocalAction {
    private final Object pauseSync = new Object();
    private volatile boolean pause = true;
    private volatile boolean pauseComplete;

    final PolyglotContextImpl context;

    PauseThreadLocalAction(PolyglotContextImpl context) {
        super(false, true);
        this.context = context;
    }

    @Override
    protected void perform(ThreadLocalAction.Access access) {
        if (access.getThread() != context.closingThread) {
            synchronized (pauseSync) {
                pauseComplete = true;
                pauseSync.notifyAll();
            }
            TruffleSafepoint.setBlockedThreadInterruptible(access.getLocation(), new TruffleSafepoint.Interruptible<Object>() {
                @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
                @Override
                public void apply(Object waitObject) throws InterruptedException {
                    synchronized (waitObject) {
                        PolyglotContextImpl.State localContextState = context.state;
                        while (pause && !localContextState.isClosed() && !localContextState.isCancelling() && !localContextState.isExiting()) {
                            waitObject.wait();
                        }
                    }
                }
            }, pauseSync);
        }
    }

    void resume() {
        synchronized (pauseSync) {
            pause = false;
            pauseSync.notifyAll();
        }
    }

    /**
     * Wait until either all active threads have called the perform method of this action, or there
     * are no active threads at all. There might be new threads started after the initial submit of
     * this action is completed and this action will also be submitted for the new threads, but we
     * assume that for these threads the context is immediately paused, so for the pause to be
     * considered done, it is sufficient to take only the first submit into account.
     */
    void waitUntilPaused(Future<?> actionFuture) throws InterruptedException {
        synchronized (pauseSync) {
            while (!pauseComplete && !actionFuture.isDone()) {
                pauseSync.wait(10);
            }
        }
    }

    void waitUntilPaused(Future<?> actionFuture, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        long timeoutTime = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(timeout, unit);
        synchronized (pauseSync) {
            while (!pauseComplete && !actionFuture.isDone() && System.currentTimeMillis() < timeoutTime) {
                long remainingTime = timeoutTime - System.currentTimeMillis();
                pauseSync.wait(Math.max(1, Math.min(10, remainingTime)));
            }
            if (!pauseComplete && !actionFuture.isDone()) {
                throw new TimeoutException("Waiting for pause timed out!");
            }
        }
    }

    boolean wasPaused(Future<?> actionFuture) {
        return pauseComplete || actionFuture.isDone();
    }

    boolean isPause() {
        return pause;
    }
}
