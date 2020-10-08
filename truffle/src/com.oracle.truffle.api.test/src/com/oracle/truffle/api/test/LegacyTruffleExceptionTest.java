/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static com.oracle.truffle.api.test.TruffleExceptionTest.createAST;
import static com.oracle.truffle.api.test.TruffleExceptionTest.createContext;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.TruffleExceptionTest.BlockNode;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LegacyTruffleExceptionTest extends AbstractPolyglotTest {

    private TruffleExceptionTest.VerifyingHandler verifyingHandler;

    @Before
    public void setUp() {
        verifyingHandler = new TruffleExceptionTest.VerifyingHandler(LegacyTruffleExceptionTest.class);
    }

    @Test
    public void testLegacyException() {
        setupEnv(createContext(verifyingHandler), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return createAST(LegacyTruffleExceptionTest.class, languageInstance, (n) -> {
                    LegacyCatchableException exception = new LegacyCatchableException("Test exception", n);
                    LangObject exceptionObject = new LangObject(exception);
                    exception.setExceptionObject(exceptionObject);
                    return exceptionObject;
                }, false);
            }
        });
        verifyingHandler.expect(BlockNode.Kind.TRY, BlockNode.Kind.CATCH, BlockNode.Kind.FINALLY);
        context.eval(ProxyLanguage.ID, "Test");
    }

    @Test
    public void testLegacyExceptionCustomGuestObject() {
        setupEnv(createContext(verifyingHandler), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return createAST(LegacyTruffleExceptionTest.class, languageInstance, (n) -> {
                    LegacyCatchableException exception = new LegacyCatchableException("Test exception", n);
                    LangObject exceptionObject = new LangObject(exception);
                    exception.setExceptionObject(exceptionObject);
                    return exceptionObject;
                }, true);
            }
        });
        verifyingHandler.expect(BlockNode.Kind.TRY, BlockNode.Kind.CATCH, BlockNode.Kind.FINALLY);
        context.eval(ProxyLanguage.ID, "Test");
    }

    @Test
    public void testLegacyCancelException() {
        setupEnv(createContext(verifyingHandler), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return createAST(LegacyTruffleExceptionTest.class, languageInstance, (n) -> {
                    LegacyCancelException exception = new LegacyCancelException(n);
                    LangObject exceptionObject = new LangObject(exception);
                    exception.setExceptionObject(exceptionObject);
                    return exceptionObject;
                }, false);
            }
        });
        verifyingHandler.expect(BlockNode.Kind.TRY);
        assertFails(() -> context.eval(ProxyLanguage.ID, "Test"), PolyglotException.class, (e) -> {
            Assert.assertTrue(e.isCancelled());
            Assert.assertNotNull(e.getGuestObject());
        });
    }

    @ExportLibrary(InteropLibrary.class)
    static final class LangObject implements TruffleObject {

        private final Throwable exception;

        LangObject(Throwable exception) {
            this.exception = exception;
        }

        @ExportMessage
        public boolean isException() {
            return exception != null;
        }

        @ExportMessage
        public ExceptionType getExceptionType() throws UnsupportedMessageException {
            if (exception == null) {
                throw UnsupportedMessageException.create();
            }
            return ExceptionType.RUNTIME_ERROR;
        }

        @ExportMessage
        public RuntimeException throwException() throws UnsupportedMessageException {
            if (exception == null) {
                throw UnsupportedMessageException.create();
            }
            throw TruffleExceptionTest.sthrow(RuntimeException.class, exception);
        }
    }

    @SuppressWarnings({"serial", "deprecation"})
    static final class LegacyCatchableException extends RuntimeException implements com.oracle.truffle.api.TruffleException, TruffleObject {

        private final Node location;
        private Object exeptionObject;

        LegacyCatchableException(String message, Node location) {
            super(message);
            this.location = location;
        }

        void setExceptionObject(Object exeptionObject) {
            this.exeptionObject = exeptionObject;
        }

        @Override
        public Node getLocation() {
            return location;
        }

        @Override
        public Object getExceptionObject() {
            return exeptionObject;
        }
    }

    @SuppressWarnings({"serial", "deprecation"})
    static final class LegacyCancelException extends ThreadDeath implements com.oracle.truffle.api.TruffleException, TruffleObject {

        private Node location;
        private Object exeptionObject;

        LegacyCancelException(Node location) {
            super();
            this.location = location;
        }

        void setExceptionObject(Object exeptionObject) {
            this.exeptionObject = exeptionObject;
        }

        @Override
        public boolean isCancelled() {
            return true;
        }

        @Override
        public Node getLocation() {
            return location;
        }

        @Override
        public Object getExceptionObject() {
            return exeptionObject;
        }
    }
}
