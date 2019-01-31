/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

public class PolyglotExceptionTest {

    @SuppressWarnings("serial")
    private static class TestGuestError extends RuntimeException implements TruffleException {

        TestGuestError() {
            super("MyError");
        }

        public Node getLocation() {
            return null;
        }

    }

    @Test
    public void testExceptionWrapping() {
        Context context = Context.create();
        Context otherContext = Context.create();

        CauseErrorTruffleObject causeError = new CauseErrorTruffleObject();
        causeError.thrownError = new TestGuestError();

        Value throwError = context.asValue(causeError);
        Value throwErrorOtherContext = otherContext.asValue(causeError);

        try {
            throwError.execute();
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertEquals(e.getMessage(), "MyError");
            Assert.assertTrue(e.isGuestException());
        }

        Value verifyError = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                try {
                    throwError.execute();
                    Assert.fail();
                } catch (PolyglotException e) {
                    Assert.assertEquals(e.getMessage(), "MyError");
                    Assert.assertTrue(e.isGuestException());
                    throw e;
                }
                return null;
            }
        });
        try {
            verifyError.execute();
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertEquals(e.getMessage(), "MyError");
            Assert.assertTrue(e.isGuestException());
        }

        // if the exception was thrown by a different context it will be treated
        // as a host exception.
        Value verifyErrorOtherContext = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                try {
                    throwErrorOtherContext.execute();
                } catch (PolyglotException e) {
                    Assert.assertEquals(e.getMessage(), "MyError");
                    Assert.assertTrue(e.isGuestException());
                    throw e;
                }
                return null;
            }
        });
        try {
            verifyErrorOtherContext.execute();
            Assert.fail();
        } catch (PolyglotException e) {
            // assert that polyglot exception was not unboxed if from other context
            Assert.assertTrue(e.asHostException() instanceof PolyglotException);
            PolyglotException polyglot = (PolyglotException) e.asHostException();
            Assert.assertEquals(polyglot.getMessage(), "MyError");
            Assert.assertTrue(polyglot.isGuestException());
        }

        context.close();
        otherContext.close();
    }

    @Test
    public void testLanguageExceptionUnwrapping() {
        Context context = Context.create();

        Value throwError = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                throw new RuntimeException();
            }
        });

        VerifyErrorTruffleObject checkErrorObj = new VerifyErrorTruffleObject();
        Value checkError = context.asValue(checkErrorObj);

        checkErrorObj.verifyError = (e) -> {
            Assert.assertTrue(e instanceof TruffleException);
            Assert.assertEquals("HostException", e.getClass().getSimpleName());
        };
        Assert.assertTrue(checkError.execute(throwError).asBoolean());

        context.close();
    }

    @ExportLibrary(InteropLibrary.class)
    static class CauseErrorTruffleObject implements TruffleObject {

        RuntimeException thrownError;

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        final Object execute(@SuppressWarnings("unused") Object[] arguments) {
            throw thrownError;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class VerifyErrorTruffleObject implements TruffleObject {

        Consumer<Throwable> verifyError;

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        final Object execute(Object[] arguments) {
            Object arg = arguments[0];
            try {
                InteropLibrary.getFactory().getUncached().execute(arg);
                Assert.fail();
            } catch (Throwable e) {
                verifyError.accept(e);
            }
            return true;
        }

    }

    @Test
    public void testRecursiveHostException() {
        Context context = Context.create();
        RuntimeException e1 = new RuntimeException();
        RuntimeException e2 = new RuntimeException();
        e1.initCause(e2);
        e2.initCause(e1);
        Value throwError = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                throw e1;
            }
        });
        try {
            throwError.execute();
            fail();
        } catch (PolyglotException e) {
            assertSame(e1, e.asHostException());
        }
    }

}
