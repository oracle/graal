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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
@ExportLibrary(value = InteropLibrary.class, receiverType = AbstractTruffleException.class)
final class DefaultAbstractTruffleExceptionExports {

    @ExportMessage
    static boolean isException(AbstractTruffleException receiver) {
        return true;
    }

    @ExportMessage
    static RuntimeException throwException(AbstractTruffleException receiver) {
        throw receiver;
    }

    @ExportMessage
    static boolean isExceptionUnwind(AbstractTruffleException receiver) {
        return false;
    }

    @ExportMessage
    static ExceptionType getExceptionType(AbstractTruffleException receiver) {
        return ExceptionType.LANGUAGE_ERROR;
    }

    @ExportMessage
    static int getExceptionExitStatus(AbstractTruffleException receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean hasSourceLocation(AbstractTruffleException receiver) {
        return receiver.getSourceLocation() != null;
    }

    @ExportMessage
    static SourceSection getSourceLocation(AbstractTruffleException receiver) throws UnsupportedMessageException {
        SourceSection sourceLocation = receiver.getSourceLocation();
        if (sourceLocation == null) {
            throw UnsupportedMessageException.create();
        }
        return sourceLocation;
    }

    @ExportMessage
    static boolean hasExceptionCause(AbstractTruffleException receiver) {
        return receiver.getCause() != null;
    }

    @ExportMessage
    static Object getExceptionCause(AbstractTruffleException receiver) throws UnsupportedMessageException {
        Throwable throwable = receiver.getCause();
        if (throwable != null) {
            return throwable;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static boolean hasExceptionSuppressed(AbstractTruffleException receiver) {
        return hasExceptionSuppressedImpl(receiver);
    }

    @ExportMessage
    static Object getExceptionSuppressed(AbstractTruffleException receiver) throws UnsupportedMessageException {
        return getExceptionSuppressedImpl(receiver);
    }

    @ExportMessage
    static boolean hasExceptionMessage(AbstractTruffleException receiver) {
        return receiver.getMessage() != null;
    }

    @ExportMessage
    static Object getExceptionMessage(AbstractTruffleException receiver) throws UnsupportedMessageException {
        String message = receiver.getMessage();
        if (message == null) {
            throw UnsupportedMessageException.create();
        } else {
            return message;
        }
    }

    @ExportMessage
    static boolean hasExceptionStackTrace(AbstractTruffleException receiver) {
        return true;
    }

    @ExportMessage
    static Object getExceptionStackTrace(AbstractTruffleException receiver) throws UnsupportedMessageException {
        return getExceptionStackTraceImpl(receiver);
    }

    @TruffleBoundary
    static boolean hasExceptionSuppressedImpl(Throwable t) {
        for (Throwable se : t.getSuppressed()) {
            if (isTruffleException(se)) {
                return true;
            }
        }
        return false;
    }

    @TruffleBoundary
    static Object getExceptionSuppressedImpl(Throwable t) throws UnsupportedMessageException {
        List<Throwable> suppressed = new ArrayList<>();
        for (Throwable se : t.getSuppressed()) {
            if (isTruffleException(se)) {
                suppressed.add(se);
            }
        }
        if (suppressed.isEmpty()) {
            throw UnsupportedMessageException.create();
        } else {
            return new InteropList(suppressed.toArray(new Throwable[suppressed.size()]));
        }
    }

    @TruffleBoundary
    static Object getExceptionStackTraceImpl(Throwable t) throws UnsupportedMessageException {
        List<TruffleStackTraceElement> stack = TruffleStackTrace.getStackTrace(t);
        if (stack == null) {
            stack = Collections.emptyList();
        }
        Object[] items = new Object[stack.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = stack.get(i).getGuestObject();
        }
        return new InteropList(items);
    }

    @SuppressWarnings("deprecation")
    private static boolean isTruffleException(Throwable t) {
        return t instanceof com.oracle.truffle.api.TruffleException;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class InteropList implements TruffleObject {

        private final Object[] items;

        InteropList(Object[] items) {
            this.items = items;
        }

        @ExportMessage
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
