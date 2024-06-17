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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.polyglot.PolyglotThreadLocalActions.TL_HANDSHAKE;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl;

abstract class SystemThread extends Thread {

    private final PolyglotImpl polyglot;

    SystemThread(Runnable runnable, ThreadGroup threadGroup, PolyglotImpl polyglot) {
        super(threadGroup, runnable);
        this.polyglot = polyglot;
    }

    abstract void beforeExecute();

    abstract void afterExecute();

    @Override
    @SuppressWarnings("try")
    public final void run() {
        AbstractPolyglotImpl rootPolyglot = polyglot.getRootImpl();
        try (AbstractPolyglotImpl.ThreadScope threadScope = rootPolyglot.createThreadScope()) {
            beforeExecute();
            TL_HANDSHAKE.ensureThreadInitialized();
            try {
                super.run();
            } finally {
                afterExecute();
            }
        }
    }

    static final class InstrumentSystemThread extends SystemThread {
        final String instrumentId;
        private final PolyglotEngineImpl engine;

        InstrumentSystemThread(PolyglotInstrument polyglotInstrument, Runnable runnable, ThreadGroup threadGroup) {
            super(runnable, threadGroup, polyglotInstrument.engine.impl);
            this.instrumentId = polyglotInstrument.getId();
            this.engine = polyglotInstrument.engine;
            checkClosed();
        }

        @Override
        void beforeExecute() {
            engine.addSystemThread(this);
            checkClosed();
        }

        @Override
        void afterExecute() {
            engine.removeSystemThread(this);
        }

        private void checkClosed() {
            if (engine.closed) {
                throw new IllegalStateException(String.format("Engine is already closed. Cannot start a new system thread for instrument %s.", instrumentId));
            }
        }
    }

    static final class LanguageSystemThread extends SystemThread {

        final String languageId;
        private final PolyglotContextImpl polyglotContext;

        LanguageSystemThread(PolyglotLanguageContext polyglotLanguageContext, Runnable runnable, ThreadGroup threadGroup) {
            super(runnable, threadGroup, polyglotLanguageContext.context.engine.impl);
            this.languageId = polyglotLanguageContext.language.getId();
            this.polyglotContext = polyglotLanguageContext.context;
            checkClosed();
        }

        @Override
        void beforeExecute() {
            polyglotContext.addSystemThread(this);
            checkClosed();
        }

        @Override
        void afterExecute() {
            polyglotContext.removeSystemThread(this);
        }

        private void checkClosed() {
            if (polyglotContext.state.isClosed()) {
                throw new IllegalStateException(String.format("Context is already closed. Cannot start a new system thread for language %s.", languageId));
            }
        }
    }
}
