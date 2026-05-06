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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;

class SandboxPauseExecutionRunnable implements Runnable {

    private final SandboxInstrument instrument;
    private volatile boolean phaserRegistered;
    private boolean shouldPauseAndOrResume;
    private boolean finished = false;
    private volatile List<ContextPauseHandleWrapper> contextsToResume;
    private long lowMemoryTriggerNumber; // for logging only

    SandboxPauseExecutionRunnable(SandboxInstrument instrument) {
        this.instrument = instrument;
    }

    /**
     * Initiate (pause contexts -> computed retained sizes -> resume contexts) operation for the
     * instrument. Resume operation can be in progress if new stop the world situation was triggered
     * and the previous one haven't finished yet.
     */
    void initiatePauseAndResume(long triggerNumber) {
        synchronized (instrument) {
            if (instrument.pauseExecutionRunnable != null) {
                SandboxLowMemoryListener.lowMemoryListener.phaser.register();
                phaserRegistered = true;
                lowMemoryTriggerNumber = triggerNumber;
                shouldPauseAndOrResume = true;
                instrument.notifyAll();
            }
        }
    }

    /**
     * Initiate resume contexts operation.
     */
    void initiateResume(long triggerNumber) {
        synchronized (instrument) {
            if (instrument.pausedSandboxContexts != null) {
                // Before the following line, shouldPauseAndOrResume must be false.
                shouldPauseAndOrResume = true;
                lowMemoryTriggerNumber = triggerNumber;
                contextsToResume = new ArrayList<>(instrument.pausedSandboxContexts);
                instrument.pausedSandboxContexts.clear();
                instrument.notifyAll();
            }
        }
    }

    /**
     * The instrument is being disposed and so the SandboxPauseExecutionRunnable for the instrument
     * have to be terminated as well.
     */
    void setFinished() {
        assert Thread.holdsLock(instrument);
        if (phaserRegistered) {
            /*
             * (pause contexts -> computed retained sizes -> resume contexts) was initiated, but
             * before it could start the instrument is being disposed and so the phaser has to be
             * deregistered as there is nothing else that would do that.
             */
            SandboxLowMemoryListener.lowMemoryListener.phaser.arriveAndDeregister();
        }
        shouldPauseAndOrResume = false;
        phaserRegistered = false;
        finished = true;
        if (contextsToResume != null) {
            instrument.pausedSandboxContexts.addAll(contextsToResume);
        }
        contextsToResume = null;
        instrument.notifyAll();
    }

    public boolean isFinished() {
        assert Thread.holdsLock(instrument);
        return finished;
    }

    /**
     * This method waits for a request initiated by the low memory trigger (stop the world
     * situation). Upon this request, it pauses all memory-limited contexts in the scope of the
     * instrument, computes their retained sizes, cancels those contexts that are over their limit,
     * and then unpauses the remaining contexts. The following facts have to be taken into account:
     *
     * 1) The instrument can be disposed at any time (setFinished is called in that case).
     *
     * 2) Additional contexts to those that were paused by this method can be paused during retained
     * size computation and those have to be resumed as well in the end. They are the contexts that
     * were newly created and immediately paused (because of being in the middle of a stop the world
     * situation).
     *
     * 3) Special case of 2) is that no contexts were paused by this method, but there are some
     * newly created and immediately paused contexts during a stop the world situation that need to
     * be resumed by this method when the stop the world situation ends.
     *
     * 4) During resuming of contexts, a new stop the world situation can be triggered, in this case
     * the resuming of remaining contexts is skipped and new pausing and retained size computation
     * round is initiated.
     *
     */
    @Override
    public void run() {
        try {
            while (true) { // TERMINATION ARGUMENT: busy waiting loop
                // wait for a (pause-compute-resume)/resume/finished request
                boolean isPhaserRegistered;
                long triggerNumber;
                synchronized (instrument) {
                    while (!shouldPauseAndOrResume && !finished) {
                        try {
                            instrument.wait();
                        } catch (InterruptedException ie) {
                        }
                    }
                    if (finished) {
                        break;
                    }
                    isPhaserRegistered = phaserRegistered;
                    triggerNumber = lowMemoryTriggerNumber;
                    shouldPauseAndOrResume = false;
                    phaserRegistered = false;
                }

                if (isPhaserRegistered) {
                    pauseContexts(triggerNumber);
                    waitTillPaused(triggerNumber);
                    SandboxMemoryLimitRetainedSizeChecker.computeAllContextsRetainedSize(instrument, lowMemoryTriggerNumber);
                }

                long startTime = System.currentTimeMillis();
                List<ContextPauseHandleWrapper> localContextsToResume = waitForResumeRequest();
                SandboxInstrument.log(instrument, "[low-memory-trigger-%d] Resume initiated for instrument.", triggerNumber);
                /*
                 * At this point the stopTheWorld can be true again and so the paused contexts can
                 * contain newly paused contexts that should not be resumed and so we only resume
                 * the contexts that were requested for resume.
                 */
                if (localContextsToResume != null) {
                    resumeContexts(localContextsToResume, triggerNumber);
                }
                long endTime = System.currentTimeMillis();
                SandboxInstrument.log(instrument, "[low-memory-trigger-%d] Instrument resumed in %dms.", triggerNumber, endTime - startTime);
            }
        } catch (Throwable t) {
            SandboxInstrument.logAlways(instrument, "SandboxPauseExecutionRunnable threw an exception", t);
        }
    }

    private List<ContextPauseHandleWrapper> waitForResumeRequest() {
        List<ContextPauseHandleWrapper> localContextsToResume = null;
        synchronized (instrument) {
            while (contextsToResume == null && !finished) {
                try {
                    instrument.wait();
                } catch (InterruptedException ie) {
                }
            }
            if (!phaserRegistered) {
                /*
                 * We unset the boolean unless there was another call to the initiatePauseAndResume
                 * method in the meantime.
                 */
                shouldPauseAndOrResume = false;
            }
            if (contextsToResume != null) {
                localContextsToResume = contextsToResume;
                contextsToResume = null;
            }
        }
        return localContextsToResume;
    }

    /**
     * Resume paused contexts. Skip resuming of the remaining contexts in case new stop the world
     * situation starts before the resuming finishes.
     *
     * @param localContextsToResume
     */
    private void resumeContexts(List<ContextPauseHandleWrapper> localContextsToResume, long triggerNumber) {
        for (int i = 0; i < localContextsToResume.size(); i++) {
            if (phaserRegistered) {
                synchronized (instrument) {
                    if (phaserRegistered) {
                        contextsToResume = new ArrayList<>(localContextsToResume.subList(i, localContextsToResume.size()));
                        SandboxInstrument.log(instrument, "[low-memory-trigger-%d] Resume interrupted due to another stop the world event.", triggerNumber);
                        break;
                    }
                }
            }
            ContextPauseHandleWrapper pausedContext = localContextsToResume.get(i);
            pausedContext.resume(triggerNumber);
        }
    }

    /**
     * Pause all context in the scope of this instrument with the exception of contexts that were
     * paused by the previous stop the world situation and their resuming was skipped.
     */
    private void pauseContexts(long triggerNumber) {
        Set<SandboxContext> alreadyPausedContexts = new HashSet<>();
        if (contextsToResume != null) {
            SandboxInstrument.log(instrument, "[low-memory-trigger-%d] Skipped resume detected.", triggerNumber);
            synchronized (instrument) {
                if (contextsToResume != null) {
                    for (ContextPauseHandleWrapper pausedContext : contextsToResume) {
                        instrument.pausedSandboxContexts.add(pausedContext);
                        alreadyPausedContexts.add(pausedContext.getContext());
                    }
                    contextsToResume = null;
                }
            }
        }
        long startTime = System.currentTimeMillis();
        TruffleInstrument.Env instrumentEnv = instrument.environment;
        if (instrumentEnv != null) {
            List<SandboxContext> limitedContexts = instrument.getMemoryLimitedSandboxContexts();
            for (SandboxContext limitedContext : limitedContexts) {
                if (!alreadyPausedContexts.contains(limitedContext)) {
                    instrument.pauseSandboxContext(this, limitedContext, true);
                }
            }
        }
        long endTime = System.currentTimeMillis();
        SandboxInstrument.log(instrument, "[low-memory-trigger-%d] Pause for instrument scheduled in %dms.", triggerNumber, endTime - startTime);
    }

    private void waitTillPaused(long triggerNumber) {
        long startTime = System.currentTimeMillis();
        SandboxInstrument.log(instrument, "[low-memory-trigger-%d] Waiting for instrument to be paused.", triggerNumber);
        List<ContextPauseHandleWrapper> pausedContexts;
        synchronized (instrument) {
            pausedContexts = new ArrayList<>(instrument.pausedSandboxContexts);
        }
        for (ContextPauseHandleWrapper pausedContext : pausedContexts) {
            pausedContext.waitUntilPaused(triggerNumber);
        }
        long endTime = System.currentTimeMillis();
        SandboxInstrument.log(instrument, "[low-memory-trigger-%d] Waiting for instrument to be paused finished in %dms.", triggerNumber, endTime - startTime);
    }
}
