/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.CancelExecution;

final class PolyglotThread extends Thread {

    private final PolyglotLanguageContext languageContext;

    private final CallTarget callTarget;

    volatile boolean hardExitNotificationThread;

    PolyglotThread(PolyglotLanguageContext languageContext, Runnable runnable, ThreadGroup group, long stackSize) {
        super(group, runnable, createDefaultName(languageContext), stackSize);
        this.languageContext = languageContext;
        setUncaughtExceptionHandler(languageContext.getPolyglotExceptionHandler());
        this.callTarget = ThreadSpawnRootNode.lookup(languageContext.getLanguageInstance());
    }

    PolyglotThread(PolyglotLanguageContext languageContext, Runnable runnable, ThreadGroup group) {
        this(languageContext, runnable, group, 0);
    }

    PolyglotThread(PolyglotLanguageContext languageContext, Runnable runnable) {
        this(languageContext, runnable, null, 0);
    }

    private static String createDefaultName(PolyglotLanguageContext creator) {
        return "Polyglot-" + creator.language.getId() + "-" + THREAD_INIT_NUMBER.getAndIncrement();
    }

    boolean isOwner(PolyglotContextImpl testContext) {
        return languageContext.context == testContext;
    }

    @Override
    public synchronized void start() {
        PolyglotContextImpl polyglotContext = languageContext.context;
        Thread hardExitTriggeringThread = polyglotContext.closeExitedTriggerThread;
        if (hardExitTriggeringThread != null) {
            Thread currentThread = currentThread();
            if (hardExitTriggeringThread == currentThread ||
                            (currentThread instanceof PolyglotThread && ((PolyglotThread) currentThread).isOwner(polyglotContext) && ((PolyglotThread) currentThread).hardExitNotificationThread)) {
                hardExitNotificationThread = true;
            }
        }
        super.start();
    }

    @Override
    public void run() {
        // always call through a HostToGuestRootNode so that stack/frame
        // walking can determine in which context the frame was executed
        callTarget.call(languageContext, this, new PolyglotThreadRunnable() {
            @Override
            @TruffleBoundary
            public void execute() {
                PolyglotThread.super.run();
            }
        });
    }

    private static final AtomicInteger THREAD_INIT_NUMBER = new AtomicInteger(0);

    // replacing Runnable with dedicated interface to avoid having Runnable.run() on the fast path,
    // since SVM will otherwise pull in all Runnable implementations as runtime compiled methods
    private interface PolyglotThreadRunnable {
        void execute();
    }

    static final class ThreadSpawnRootNode extends RootNode {

        ThreadSpawnRootNode(PolyglotLanguageInstance languageInstance) {
            super(languageInstance.spi);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] args = frame.getArguments();
            return executeImpl((PolyglotLanguageContext) args[0], (PolyglotThread) args[1], (PolyglotThreadRunnable) args[2]);
        }

        @TruffleBoundary
        private static Object executeImpl(PolyglotLanguageContext languageContext, PolyglotThread thread, PolyglotThreadRunnable run) {
            Object[] prev = languageContext.enterThread(thread);
            assert prev == null; // is this assertion correct?
            try {
                run.execute();
            } catch (CancelExecution cancel) {
                if (PolyglotEngineOptions.TriggerUncaughtExceptionHandlerForCancel.getValue(languageContext.context.engine.getEngineOptionValues())) {
                    throw cancel;
                } else {
                    return null;
                }
            } finally {
                languageContext.leaveAndDisposePolyglotThread(prev, thread);
            }
            return null;
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        public static CallTarget lookup(PolyglotLanguageInstance languageInstance) {
            CallTarget target = languageInstance.lookupCallTarget(ThreadSpawnRootNode.class);
            if (target == null) {
                target = languageInstance.installCallTarget(new ThreadSpawnRootNode(languageInstance));
            }
            return target;
        }
    }
}
