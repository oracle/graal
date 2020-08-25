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
package com.oracle.truffle.api.exception;

import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.SourceSection;

final class ExceptionAccessor extends Accessor {

    static final ExceptionAccessor ACCESSOR = new ExceptionAccessor();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();

    private ExceptionAccessor() {
    }

    static final class ExceptionSupportImpl extends ExceptionSupport {

        @Override
        public Throwable getLazyStackTrace(Throwable exception) {
            if (exception instanceof AbstractTruffleException) {
                return ((AbstractTruffleException) exception).getLazyStackTrace();
            }
            return null;
        }

        @Override
        public boolean isException(Object receiver) {
            return receiver instanceof AbstractTruffleException;
        }

        @Override
        public RuntimeException throwException(Object receiver) {
            throw DefaultAbstractTruffleExceptionExports.throwException((AbstractTruffleException) receiver);
        }

        @Override
        public boolean isExceptionUnwind(Object receiver) {
            return DefaultAbstractTruffleExceptionExports.isExceptionUnwind((AbstractTruffleException) receiver);
        }

        @Override
        public Object getExceptionType(Object receiver) {
            return DefaultAbstractTruffleExceptionExports.getExceptionType((AbstractTruffleException) receiver);
        }

        @Override
        public boolean isExceptionIncompleteSource(Object receiver) {
            return DefaultAbstractTruffleExceptionExports.isExceptionIncompleteSource((AbstractTruffleException) receiver);
        }

        @Override
        public int getExceptionExitStatus(Object receiver) {
            try {
                return DefaultAbstractTruffleExceptionExports.getExceptionExitStatus((AbstractTruffleException) receiver);
            } catch (UnsupportedMessageException me) {
                throw silenceException(RuntimeException.class, me);
            }
        }

        @Override
        public boolean hasExceptionCause(Object receiver) {
            return DefaultAbstractTruffleExceptionExports.hasExceptionCause((AbstractTruffleException) receiver);
        }

        @Override
        public Object getExceptionCause(Object receiver) {
            try {
                return DefaultAbstractTruffleExceptionExports.getExceptionCause((AbstractTruffleException) receiver);
            } catch (UnsupportedMessageException me) {
                throw silenceException(RuntimeException.class, me);
            }
        }

        @Override
        public boolean hasExceptionSuppressed(Object receiver) {
            return DefaultAbstractTruffleExceptionExports.hasExceptionSuppressedImpl((Throwable) receiver);
        }

        @Override
        public Object getExceptionSuppressed(Object receiver) {
            try {
                return DefaultAbstractTruffleExceptionExports.getExceptionSuppressedImpl((Throwable) receiver);
            } catch (UnsupportedMessageException me) {
                throw silenceException(RuntimeException.class, me);
            }
        }

        @Override
        public boolean hasExceptionMessage(Object receiver) {
            return DefaultAbstractTruffleExceptionExports.hasExceptionMessage((AbstractTruffleException) receiver);
        }

        @Override
        public Object getExceptionMessage(Object receiver) {
            try {
                return DefaultAbstractTruffleExceptionExports.getExceptionMessage((AbstractTruffleException) receiver);
            } catch (UnsupportedMessageException me) {
                throw silenceException(RuntimeException.class, me);
            }
        }

        @Override
        public boolean hasExceptionStackTrace(Object receiver) {
            return DefaultAbstractTruffleExceptionExports.hasExceptionStackTrace((AbstractTruffleException) receiver);
        }

        @Override
        public Object getExceptionStackTrace(Object receiver) {
            try {
                return DefaultAbstractTruffleExceptionExports.getExceptionStackTraceImpl((Throwable) receiver);
            } catch (UnsupportedMessageException me) {
                throw silenceException(RuntimeException.class, me);
            }
        }

        @Override
        public boolean hasSourceLocation(Object receiver) {
            return DefaultAbstractTruffleExceptionExports.hasSourceLocation((AbstractTruffleException) receiver);
        }

        @Override
        public SourceSection getSourceLocation(Object receiver) {
            try {
                return DefaultAbstractTruffleExceptionExports.getSourceLocation((AbstractTruffleException) receiver);
            } catch (UnsupportedMessageException me) {
                throw silenceException(RuntimeException.class, me);
            }
        }

        @SuppressWarnings({"unchecked", "unused"})
        private static <E extends Throwable> RuntimeException silenceException(Class<E> type, Throwable ex) throws E {
            throw (E) ex;
        }
    }

}
