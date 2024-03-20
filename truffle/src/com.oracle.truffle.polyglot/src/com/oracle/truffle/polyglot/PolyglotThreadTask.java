/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleLanguage;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.ThreadScope;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.CancelExecution;

/**
 * Information about a polyglot thread, that is a Thread created through
 * {@link TruffleLanguage.Env#newTruffleThreadBuilder}.build().
 */
final class PolyglotThreadTask implements Runnable {

    public static final PolyglotThreadTask ISOLATE_POLYGLOT_THREAD = new PolyglotThreadTask();

    private static final AtomicInteger THREAD_INIT_NUMBER = new AtomicInteger(0);

    private final PolyglotLanguageContext languageContext;
    private final Runnable userRunnable;
    final Thread parentThread;
    final Runnable beforeEnter;
    final Runnable afterLeave;
    private final CallTarget callTarget;

    PolyglotThreadTask(PolyglotLanguageContext languageContext, Runnable runnable, Runnable beforeEnter, Runnable afterLeave) {
        this.languageContext = languageContext;
        this.userRunnable = runnable;
        this.parentThread = Thread.currentThread();
        this.beforeEnter = beforeEnter;
        this.afterLeave = afterLeave;
        this.callTarget = ThreadSpawnRootNode.lookup(languageContext.getLanguageInstance());
    }

    private PolyglotThreadTask() {
        this.languageContext = null;
        this.userRunnable = null;
        this.parentThread = null;
        this.beforeEnter = null;
        this.afterLeave = null;
        this.callTarget = null;
    }

    static String createDefaultName(PolyglotLanguageContext creator) {
        return "Polyglot-" + creator.language.getId() + "-" + THREAD_INIT_NUMBER.getAndIncrement();
    }

    @Override
    public void run() {
        // always call through a HostToGuestRootNode so that stack/frame
        // walking can determine in which context the frame was executed
        try {
            callTarget.call(null, languageContext, this, userRunnable);
        } catch (Throwable t) {
            throw PolyglotImpl.engineToLanguageException(t);
        }
    }

    static final class ThreadSpawnRootNode extends RootNode {

        ThreadSpawnRootNode(PolyglotLanguageInstance languageInstance) {
            super(languageInstance.spi);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] args = frame.getArguments();
            return executeImpl((PolyglotLanguageContext) args[0], (PolyglotThreadTask) args[1], (Runnable) args[2]);
        }

        @TruffleBoundary
        @SuppressWarnings("try")
        private static Object executeImpl(PolyglotLanguageContext languageContext, PolyglotThreadTask polyglotThreadTask, Runnable runnable) {
            Object[] prev = languageContext.enterThread(polyglotThreadTask);
            assert prev == null; // is this assertion correct?
            try (ThreadScope scope = languageContext.getImpl().getRootImpl().createThreadScope()) {
                runnable.run();
            } catch (CancelExecution cancel) {
                if (PolyglotEngineOptions.TriggerUncaughtExceptionHandlerForCancel.getValue(languageContext.context.engine.getEngineOptionValues())) {
                    throw cancel;
                } else {
                    return null;
                }
            } finally {
                languageContext.leaveAndDisposePolyglotThread(prev, polyglotThreadTask);
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
