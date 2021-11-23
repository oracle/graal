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
package com.oracle.truffle.api.test.polyglot;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class LanguageFinalizationFailureTest extends AbstractPolyglotTest {
    private static final Node DUMMY_NODE = new Node() {
    };

    @ExportLibrary(InteropLibrary.class)
    static final class DummyRuntimeException extends AbstractTruffleException {
        private static final long serialVersionUID = 5292066718048069141L;

        DummyRuntimeException(Node location) {
            super("Dummy runtime error.", location);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        ExceptionType getExceptionType() {
            return ExceptionType.RUNTIME_ERROR;
        }
    }

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void testFinalizationFailureTruffleException() {
        AtomicBoolean disposeCalled = new AtomicBoolean();
        setupEnv(Context.newBuilder(), new ProxyLanguage() {
            @Override
            protected void finalizeContext(LanguageContext languageContext) {
                throw new DummyRuntimeException(DUMMY_NODE);
            }

            @Override
            protected void disposeContext(LanguageContext languageContext) {
                disposeCalled.set(true);
            }
        });
        try {
            context.close();
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!"Dummy runtime error.".equals(pe.getMessage())) {
                throw pe;
            }
            Assert.assertFalse(pe.isInternalError());
        }
        Assert.assertFalse(disposeCalled.get());
        context.close(true);
        Assert.assertTrue(disposeCalled.get());
    }

    @Test
    public void testFinalizationFailureCancelException() {
        AtomicBoolean disposeCalled = new AtomicBoolean();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        setupEnv(Context.newBuilder().option("log.engine.level", "FINE").logHandler(outStream), new ProxyLanguage() {
            @Override
            protected void finalizeContext(LanguageContext languageContext) {
                TruffleSafepoint.pollHere(DUMMY_NODE);
                Assert.fail();
            }

            @Override
            protected void disposeContext(LanguageContext languageContext) {
                disposeCalled.set(true);
            }

        });
        context.close(true);
        Assert.assertTrue(disposeCalled.get());
        Assert.assertTrue(outStream.toString().contains(
                        "Exception was thrown while finalizing a polyglot context that is being cancelled or exited. Such exceptions are expected during cancelling or exiting."));
    }

    @Test
    public void testDisposeFailure() {
        AtomicBoolean failDispose = new AtomicBoolean(true);
        setupEnv(Context.newBuilder(), new ProxyLanguage() {
            @Override
            protected void disposeContext(LanguageContext languageContext) {
                if (failDispose.get()) {
                    throw new DummyRuntimeException(DUMMY_NODE);
                }
            }
        });
        try {
            context.close();
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!"java.lang.IllegalStateException: Guest language code was run during language disposal!".equals(pe.getMessage())) {
                throw pe;
            }
            Assert.assertTrue(pe.isInternalError());
        }
        failDispose.set(false);
        context.close();
    }
}
