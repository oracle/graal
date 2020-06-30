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

import static com.oracle.truffle.api.interop.ExceptionType.CANCEL;
import static com.oracle.truffle.api.interop.ExceptionType.EXIT;
import static com.oracle.truffle.api.interop.ExceptionType.GUEST_LANGUAGE_ERROR;
import static com.oracle.truffle.api.interop.ExceptionType.INCOMPLETE_SOURCE;
import static com.oracle.truffle.api.interop.ExceptionType.INTERNAL_ERROR;
import static com.oracle.truffle.api.interop.ExceptionType.SYNTAX_ERROR;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings({"unused", "deprecation"})
@ExportLibrary(value = InteropLibrary.class, receiverType = com.oracle.truffle.api.TruffleException.class)
final class DefaultLegacyTruffleExceptionExports {

    @ExportMessage
    static boolean isException(com.oracle.truffle.api.TruffleException receiver) {
        return true;
    }

    @ExportMessage
    static RuntimeException throwException(com.oracle.truffle.api.TruffleException receiver) {
        throw sthrow(RuntimeException.class, (Throwable) receiver);
    }

    @ExportMessage
    static boolean isExceptionCatchable(com.oracle.truffle.api.TruffleException receiver) {
        return !(receiver instanceof ThreadDeath);
    }

    @ExportMessage
    static ExceptionType getExceptionType(com.oracle.truffle.api.TruffleException receiver) {
        if (receiver.isCancelled()) {
            return CANCEL;
        } else if (receiver.isExit()) {
            return EXIT;
        } else if (receiver.isIncompleteSource()) {
            return INCOMPLETE_SOURCE;
        } else if (receiver.isInternalError()) {
            return INTERNAL_ERROR;
        } else if (receiver.isSyntaxError()) {
            return SYNTAX_ERROR;
        } else {
            return GUEST_LANGUAGE_ERROR;
        }
    }

    @ExportMessage
    static int getExceptionExitStatus(com.oracle.truffle.api.TruffleException receiver) {
        return receiver.getExitStatus();
    }

    @ExportMessage
    static boolean hasSourceLocation(com.oracle.truffle.api.TruffleException receiver) {
        return receiver.getSourceLocation() != null;
    }

    @ExportMessage
    static SourceSection getSourceLocation(com.oracle.truffle.api.TruffleException receiver) throws UnsupportedMessageException {
        SourceSection sourceLocation = receiver.getSourceLocation();
        if (sourceLocation == null) {
            throw UnsupportedMessageException.create();
        }
        return sourceLocation;
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T sthrow(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }
}
