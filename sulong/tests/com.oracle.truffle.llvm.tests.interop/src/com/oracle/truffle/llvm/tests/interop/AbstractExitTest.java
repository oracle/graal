/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

import static org.junit.Assume.assumeFalse;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import com.oracle.truffle.api.InternalResource.OS;

public abstract class AbstractExitTest {

    protected static final CountDownLatch startSignal = new CountDownLatch(1);
    protected static final int EXIT_CODE = 3;

    protected Value testModule;
    protected Value testLoop;
    protected Value installExitHook;
    protected ExitHookCallback exitHookCallback;

    @BeforeClass
    public static void bundledOnly() {
        InteropTestBase.bundledOnly();
    }

    protected void afterContextClosed() {
        Assert.assertTrue(exitHookCallback.called);
    }

    @Rule
    public TestRule getHookCheck() {
        return (base, description) -> new Statement() {
            final Statement next = InteropTestBase.runWithPolyglot.apply(base, description);

            @Override
            public void evaluate() throws Throwable {
                next.evaluate();
                afterContextClosed();
            }
        };
    }

    @ExportLibrary(InteropLibrary.class)
    static class ExitHookCallback implements TruffleObject {

        boolean called;

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(@SuppressWarnings("unused") Object... arguments) {
            called = true;
            return 0;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class LoopCallback implements TruffleObject {

        final Consumer<Integer> notif;

        LoopCallback(Consumer<Integer> notif) {
            this.notif = notif;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        Object execute(Object... arguments) {
            try {
                int counter = InteropLibrary.getUncached().asInt(arguments[0]);
                notif.accept(counter);
                // return counter < 10 ? 0 : 1;
                return 0;
            } catch (UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Before
    public void prepareTest() {
        assumeFalse("Truffle exit tests are not supported on Windows", InteropTestBase.getOS() == OS.WINDOWS);
        testModule = InteropTestBase.loadTestBitcodeValue("truffleExitTest.c");

        installExitHook = testModule.getMember("installExitHook");
        exitHookCallback = new ExitHookCallback();
        boolean supportsHandles = LLVMLanguage.get(null).getCapability(LLVMMemory.class).supportsHandles();
        installExitHook.execute(exitHookCallback, supportsHandles);

        testLoop = testModule.getMember("testLoop");
    }

    class LoopThread extends Thread {

        final Consumer<PolyglotException> exceptionHandler;

        LoopThread(Consumer<PolyglotException> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void run() {
            try {
                testLoop.execute(new LoopCallback(counter -> {
                    if (counter == 0) {
                        startSignal.countDown();
                    }
                }));
            } catch (PolyglotException pe) {
                exceptionHandler.accept(pe);
            }
        }
    }
}
