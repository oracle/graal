/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;

public class ThreadInitializationAndDisposalTest {

    @TruffleLanguage.Registration
    static class DeadThreadDisposedTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(DeadThreadDisposedTestLanguage.class);

        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return Thread.currentThread().threadId();
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected void initializeThread(ExecutableContext context, Thread thread) {
            markThreadOperationDone(context, "initialized", thread);
        }

        @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
        private void markThreadOperationDone(ExecutableContext context, String operationId, Thread thread) {
            synchronized (context) {
                try {
                    Object scope = getScope(context);
                    InteropLibrary uncached = InteropLibrary.getUncached();
                    String alreadyExecuted = "";
                    if (uncached.isMemberReadable(scope, operationId)) {
                        alreadyExecuted = uncached.readMember(scope, operationId) + ",";
                    }
                    uncached.writeMember(scope, operationId, alreadyExecuted + thread.threadId());
                } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException e) {
                    throw new AssertionError(e);
                }
            }
        }

        @Override
        protected void finalizeThread(ExecutableContext context, Thread thread) {
            markThreadOperationDone(context, "finalized", thread);
        }

        @Override
        protected void disposeThread(ExecutableContext context, Thread thread) {
            markThreadOperationDone(context, "disposed", thread);
        }
    }

    @Test
    public void testDeadThreadDisposed() throws InterruptedException {
        /*
         * Context pre-initialization enters the context on the main thread, but we need the first
         * enter on the main thread to occur after the separate thread is joined, so that it causes
         * the separate thread's disposal.
         */
        try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.UsePreInitializedContext", "false").build()) {
            AtomicLong threadId = new AtomicLong();
            Thread t = new Thread(() -> threadId.set(AbstractExecutableTestLanguage.evalTestLanguage(context, DeadThreadDisposedTestLanguage.class, "").asLong()));
            t.start();
            t.join();
            Assert.assertFalse(t.isAlive());
            long mainThreadId = AbstractExecutableTestLanguage.evalTestLanguage(context, DeadThreadDisposedTestLanguage.class, "").asLong();
            Assert.assertEquals(threadId.get() + "," + mainThreadId, context.getBindings(DeadThreadDisposedTestLanguage.ID).getMember("initialized").asString());
            Assert.assertEquals(String.valueOf(threadId.get()), context.getBindings(DeadThreadDisposedTestLanguage.ID).getMember("finalized").asString());
            Assert.assertEquals(String.valueOf(threadId.get()), context.getBindings(DeadThreadDisposedTestLanguage.ID).getMember("disposed").asString());
        }
    }
}
