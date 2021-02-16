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

import static com.oracle.truffle.api.exception.AbstractTruffleException.isTruffleException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Collections;
import java.util.List;

final class ExceptionAccessor extends Accessor {

    static final ExceptionAccessor ACCESSOR = new ExceptionAccessor();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();

    private ExceptionAccessor() {
    }

    static final class ExceptionSupportImpl extends ExceptionSupport {

        @Override
        public Throwable getLazyStackTrace(Throwable exception) {
            return ((AbstractTruffleException) exception).getLazyStackTrace();
        }

        @Override
        public void setLazyStackTrace(Throwable exception, Throwable stackTrace) {
            ((AbstractTruffleException) exception).setLazyStackTrace(stackTrace);
        }

        @Override
        public Object createDefaultStackTraceElementObject(RootNode rootNode, SourceSection sourceSection) {
            return new DefaultStackTraceElementObject(rootNode, sourceSection);
        }

        @Override
        public boolean isException(Object receiver) {
            return receiver instanceof AbstractTruffleException;
        }

        @Override
        public RuntimeException throwException(Object receiver) {
            throw (AbstractTruffleException) receiver;
        }

        @Override
        public Object getExceptionType(Object receiver) {
            return ExceptionType.RUNTIME_ERROR;
        }

        @Override
        public boolean isExceptionIncompleteSource(Object receiver) {
            return false;
        }

        @Override
        public int getExceptionExitStatus(Object receiver) {
            throw throwUnsupportedMessageException();
        }

        @Override
        public boolean hasExceptionCause(Object receiver) {
            return isTruffleException(((AbstractTruffleException) receiver).getCause());
        }

        @Override
        public Object getExceptionCause(Object receiver) {
            Throwable throwable = ((AbstractTruffleException) receiver).getCause();
            if (isTruffleException(throwable)) {
                return throwable;
            } else {
                throw throwUnsupportedMessageException();
            }
        }

        @Override
        @TruffleBoundary
        public boolean hasExceptionMessage(Object receiver) {
            return ((AbstractTruffleException) receiver).getMessage() != null;
        }

        @Override
        @TruffleBoundary
        public Object getExceptionMessage(Object receiver) {
            String message = ((AbstractTruffleException) receiver).getMessage();
            if (message == null) {
                throw throwUnsupportedMessageException();
            } else {
                return message;
            }
        }

        @Override
        public boolean hasExceptionStackTrace(Object receiver) {
            return true;
        }

        @Override
        @TruffleBoundary
        public Object getExceptionStackTrace(Object receiver) {
            List<TruffleStackTraceElement> stack = TruffleStackTrace.getStackTrace((Throwable) receiver);
            if (stack == null) {
                stack = Collections.emptyList();
            }
            Object[] items = new Object[stack.size()];
            for (int i = 0; i < items.length; i++) {
                items[i] = stack.get(i).getGuestObject();
            }
            return new InteropList(items);
        }

        @Override
        public boolean hasSourceLocation(Object receiver) {
            Node location = ((AbstractTruffleException) receiver).getLocation();
            return location != null && location.getEncapsulatingSourceSection() != null;
        }

        @Override
        public SourceSection getSourceLocation(Object receiver) {
            Node location = ((AbstractTruffleException) receiver).getLocation();
            SourceSection sourceSection = location != null ? location.getEncapsulatingSourceSection() : null;
            if (sourceSection == null) {
                throw throwUnsupportedMessageException();
            }
            return sourceSection;
        }

        @Override
        public boolean assertGuestObject(Object guestObject) {
            if (guestObject == null) {
                throw new AssertionError("Guest object must be null.");
            }
            InteropLibrary interop = InteropLibrary.getUncached();
            if (interop.hasExecutableName(guestObject)) {
                Object executableName;
                try {
                    executableName = interop.getExecutableName(guestObject);
                } catch (UnsupportedMessageException um) {
                    throw new AssertionError("Failed to get the executable name.", um);
                }
                if (!interop.isString(executableName)) {
                    throw new AssertionError("Executable name must be an interop string.");
                }
            }
            if (interop.hasDeclaringMetaObject(guestObject)) {
                Object metaObject;
                try {
                    metaObject = interop.getDeclaringMetaObject(guestObject);
                } catch (UnsupportedMessageException um) {
                    throw new AssertionError("Failed to get the declaring meta object.", um);
                }
                if (!interop.isMetaObject(metaObject)) {
                    throw new AssertionError("Declaring meta object must be an interop meta object");
                }
            }
            return true;
        }

        private static RuntimeException throwUnsupportedMessageException() {
            throw silenceException(RuntimeException.class, UnsupportedMessageException.create());
        }

        @SuppressWarnings({"unchecked", "unused"})
        private static <E extends Throwable> E silenceException(Class<E> type, Throwable ex) throws E {
            throw (E) ex;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class InteropList implements TruffleObject {

        private final Object[] items;

        InteropList(Object[] items) {
            this.items = items;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return items.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < items.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index < 0 || index >= items.length) {
                throw InvalidArrayIndexException.create(index);
            }
            return items[(int) index];
        }
    }
}
