/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.oracle.truffle.llvm.tests.interop.InteropTestBase.runWithPolyglot;

public class HardExitTest extends AbstractExitTest {
    @Test
    public void testHardExit() {
        final AtomicBoolean expectedEnd = new AtomicBoolean();
        LoopThread loopThread = new LoopThread(pe -> {
            expectedEnd.set(pe.isExit() && pe.getExitStatus() == EXIT_CODE);
        });
        loopThread.start();

        try {
            // wait until the test loop starts looping
            Assert.assertTrue(startSignal.await(10, TimeUnit.SECONDS));

            try {
                runWithPolyglot.getPolyglotContext().eval(TruffleExitTestLanguage.ID, "");
                Assert.fail();
            } catch (PolyglotException pe) {
                Assert.assertTrue(pe.isExit() && pe.getExitStatus() == EXIT_CODE);
            }

            loopThread.join(10000);

            Assert.assertTrue(expectedEnd.get());

        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
    }

    @TruffleLanguage.Registration(id = TruffleExitTestLanguage.ID, name = "truffleexittest", version = "", contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    public static class TruffleExitTestLanguage extends TruffleLanguage<TruffleExitTestLanguage.LanguageContext> {

        public static final String ID = "truffleexittest";

        @Override
        protected LanguageContext createContext(Env env) {
            return new LanguageContext(env);
        }

        @Override
        protected void initializeContext(LanguageContext context) throws Exception {
        }

        @Override
        protected CallTarget parse(ParsingRequest request) {
            return new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    LanguageContext languageContext = LanguageContext.get(this);
                    languageContext.getEnv().getContext().closeExited(this, EXIT_CODE);
                    return 0;
                }
            }.getCallTarget();
        }

        @Override
        protected void exitContext(LanguageContext ctx, ExitMode exitMode, int exitCode) {
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        public static class LanguageContext {
            final Env env;

            LanguageContext(Env env) {
                this.env = env;
            }

            public Env getEnv() {
                return env;
            }

            private static final ContextReference<LanguageContext> REFERENCE = ContextReference.create(TruffleExitTestLanguage.class);

            public static LanguageContext get(Node node) {
                return REFERENCE.get(node);
            }
        }

    }
}
