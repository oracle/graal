/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sandbox;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.ThreadsActivationListener;

final class SandboxActivationListener implements ThreadsActivationListener {

    private final SandboxInstrument instrument;

    SandboxActivationListener(SandboxInstrument instrument) {
        this.instrument = instrument;
    }

    @Override
    public void onEnterThread(TruffleContext context) {
        /*
         * context must not be passed to the get method below, otherwise it would not be efficient.
         * It is not necessary to pass it as it is always the current context.
         */
        SandboxThreadContext threadContext = instrument.sandboxThreadContext.get();
        boolean priorityNeeded = !instrument.noPriorityChangeNeeded.isValid();
        boolean timingNeeded = !instrument.noThreadTimingNeeded.isValid();
        boolean threadCountNeeded = !instrument.noThreadCountNeeded.isValid();
        boolean memoryTrackingNeeded = !instrument.noThreadAllocationTrackingNeeded.isValid();

        int enteredCount = 0;
        if (priorityNeeded || timingNeeded || threadCountNeeded || memoryTrackingNeeded) {
            enteredCount = ++threadContext.enteredCount;
        }
        if (priorityNeeded && !threadContext.deprioritized) {
            lowerPriority(threadContext);
        }
        if ((timingNeeded || memoryTrackingNeeded || threadCountNeeded) && enteredCount == 1) {
            SandboxContext currentContext = instrument.sandboxContext.get(context);
            int threadCount = currentContext.threadCounter.incrementAndGet();
            if (timingNeeded || memoryTrackingNeeded) {
                if (threadCount == 1) {
                    if (timingNeeded) {
                        scheduleChecker(currentContext, instrument.timeCheckerScheduler, currentContext.timeLimitChecker);
                    }
                    if (memoryTrackingNeeded) {
                        scheduleChecker(currentContext, instrument.memoryCheckerScheduler, currentContext.memoryLimitChecker);
                    }
                    long newChangedActiveStatisCount = currentContext.changedActiveStatusCount + 1;
                    currentContext.changedActiveStatusCount = newChangedActiveStatisCount;
                }
                if (timingNeeded) {
                    threadContext.lastEntered = threadContext.getTime(Thread.currentThread());
                }
                if (memoryTrackingNeeded) {
                    threadContext.lastAllocatedBytesSnapshot = SandboxThreadContext.getThreadAllocatedBytes(Thread.currentThread(), 0);
                }
            }
            if (threadCountNeeded) {
                if (currentContext.isTracingEnabled()) {
                    currentContext.maxActiveThreadsTraced = Math.max(currentContext.maxActiveThreadsTraced, threadCount);
                }

                if (currentContext.hasActiveThreadsLimit() && threadCount > currentContext.activeThreadsLimit) { // overflowed
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    String message = String.format("Maximum number of active threads %s exceeded. Active threads: %s.",
                                    currentContext.activeThreadsLimit, threadCount);
                    SandboxInstrument.submitOrPerformCancel(currentContext, null, message);
                }
            }
        }
    }

    /*
     * When the context has been inactive for a while, its checker may have been unscheduled, so
     * when the context becomes active again we need to check, under the lock, whether the checker
     * is scheduled or not, and add it if it is not.
     */
    private static void scheduleChecker(SandboxContext currentContext, SandboxCheckerScheduler checkerScheduler, SandboxCheckerScheduler.SandboxChecker checker) {
        if (checker != null && checker.needsSchedule()) {
            scheduleAndLog(currentContext, checkerScheduler, checker);
        }
    }

    @TruffleBoundary
    private static void scheduleAndLog(SandboxContext currentContext, SandboxCheckerScheduler multiContextChecker, SandboxCheckerScheduler.SandboxChecker contextChecker) {
        if (multiContextChecker.scheduleChecker(contextChecker)) {
            contextChecker.logReactivatedContext(currentContext);
        }
    }

    @Override
    public void onLeaveThread(TruffleContext context) {
        /*
         * context must not be passed to the get method below, otherwise it would not be efficient.
         * It is not necessary to pass it as it is always the current context.
         */
        SandboxThreadContext threadContext = instrument.sandboxThreadContext.get();
        boolean priorityNeeded = !instrument.noPriorityChangeNeeded.isValid();
        boolean timingNeeded = !instrument.noThreadTimingNeeded.isValid();
        boolean threadCountNeeded = !instrument.noThreadCountNeeded.isValid();
        boolean memoryTrackingNeeded = !instrument.noThreadAllocationTrackingNeeded.isValid();

        int count = 0;
        if (priorityNeeded || timingNeeded || threadCountNeeded || memoryTrackingNeeded) {
            count = --threadContext.enteredCount;
            if (count < 0) {
                /*
                 * The activation listener may be attached while this context is already entered,
                 * in which case onLeaveThread is called without a matching onEnterThread.
                 */
                threadContext.enteredCount = 0;
                return;
            }
        }

        if ((timingNeeded || memoryTrackingNeeded || threadCountNeeded) && count == 0) {
            SandboxContext currentContext = instrument.sandboxContext.get(context);
            int threadsCount = currentContext.threadCounter.decrementAndGet();
            if (timingNeeded || memoryTrackingNeeded) {
                if (threadsCount == 0) {
                    long newChangedActiveStatisCount = currentContext.changedActiveStatusCount + 1;
                    currentContext.changedActiveStatusCount = newChangedActiveStatisCount;
                }
                if (timingNeeded) {
                    long last = threadContext.lastEntered;
                    threadContext.lastEntered = -1;
                    if (last >= 0) {
                        long time = threadContext.timeExecuted + threadContext.getTime(Thread.currentThread()) - last;
                        threadContext.timeExecuted = time;
                    }
                }
                if (memoryTrackingNeeded) {
                    long lastBytes = threadContext.lastAllocatedBytesSnapshot;
                    threadContext.lastAllocatedBytesSnapshot = -1;
                    if (lastBytes >= 0) {
                        long bytes = threadContext.bytesAllocated + SandboxThreadContext.getThreadAllocatedBytes(Thread.currentThread(), lastBytes) - lastBytes;
                        threadContext.bytesAllocated = bytes;
                    }
                }
            }
        }

        if (priorityNeeded && threadContext.deprioritized && count == 0) {
            raisePriority(threadContext);
        }
    }

    @TruffleBoundary
    private static void lowerPriority(SandboxThreadContext threadContext) {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        threadContext.deprioritized = true;
    }

    @TruffleBoundary
    private static void raisePriority(SandboxThreadContext threadContext) {
        // this will be ineffective unless the JVM runs with corresponding system privileges
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        threadContext.deprioritized = false;
    }

}
