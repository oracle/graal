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

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.oracle.truffle.llvm.tests.interop.InteropTestBase.runWithPolyglot;

public class CancelTest extends AbstractExitTest {

    private static final String CANCEL_TEST_MSG = "cancel test";

    @Override
    protected void afterContextClosed() {
        // The hook is guest code and as such it cannot be invoked when cancelling
        Assert.assertFalse(exitHookCallback.called);
    }

    @Test
    public void testCancel() {
        final AtomicBoolean expectedEnd = new AtomicBoolean();
        AbstractExitTest.LoopThread loopThread = new AbstractExitTest.LoopThread(pe -> {
            expectedEnd.set(pe.isCancelled() && pe.getMessage().contains(CANCEL_TEST_MSG));
        });
        loopThread.start();

        try {
            // wait until the test loop starts looping
            Assert.assertTrue(startSignal.await(10, TimeUnit.SECONDS));

            try {
                TruffleLanguage.Env env = runWithPolyglot.getTruffleTestEnv();
                InstrumentInfo cancelInstrumentInfo = env.getInstruments().get(TruffleCloseInstrument.ID);
                TruffleCloseInstrument.Service cancelService = env.lookup(cancelInstrumentInfo, TruffleCloseInstrument.Service.class);
                cancelService.cancel(CANCEL_TEST_MSG);
                Assert.fail();
            } catch (ThreadDeath td) {
                Assert.assertTrue(td.getMessage().contains(CANCEL_TEST_MSG));
            }

            loopThread.join(10000);

            Assert.assertTrue(expectedEnd.get());

        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
    }

    @TruffleInstrument.Registration(id = TruffleCloseInstrument.ID, name = TruffleCloseInstrument.ID, version = "1.0", services = TruffleCloseInstrument.Service.class)
    public static final class TruffleCloseInstrument extends TruffleInstrument {

        static final String ID = "TruffleCancelTestInstrument";

        @Override
        protected void onCreate(Env env) {
            env.registerService((Service) reason -> env.getEnteredContext().closeCancelled(null, reason));
        }

        public interface Service {
            void cancel(String reason);
        }
    }
}
