/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(InteropLibrary.class)
final class HostStackTraceElementObject implements TruffleObject {

    private final StackTraceElement stackTraceElement;

    HostStackTraceElementObject(StackTraceElement stackTraceElement) {
        this.stackTraceElement = Objects.requireNonNull(stackTraceElement);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExecutableName() {
        return true;
    }

    @ExportMessage
    Object getExecutableName() {
        return stackTraceElement.getMethodName();
    }

    @ExportMessage
    boolean hasSourceLocation() {
        return stackTraceElement.getLineNumber() >= 0;
    }

    @ExportMessage
    @TruffleBoundary
    SourceSection getSourceLocation() throws UnsupportedMessageException {
        int lineNumber = stackTraceElement.getLineNumber();
        if (lineNumber >= 0) {
            Source dummySource = Source.newBuilder("host", "", stackTraceElement.getFileName()).content(Source.CONTENT_NONE).cached(false).build();
            return dummySource.createSection(lineNumber);
        }
        throw UnsupportedMessageException.create();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasDeclaringMetaObject() {
        return true;
    }

    @ExportMessage
    Object getDeclaringMetaObject() {
        return new DeclaringMetaObject();
    }

    @ExportLibrary(InteropLibrary.class)
    final class DeclaringMetaObject implements TruffleObject {

        @ExportMessage
        boolean isMetaObject() {
            return true;
        }

        @ExportMessage
        Object getMetaQualifiedName() {
            return stackTraceElement.getClassName();
        }

        /**
         * Intentionally does not handle inner classes the same way as {@link Class#getSimpleName()}
         * since, without the {@link Class} at hand, we cannot know for sure if {@code $} is an
         * inner class separator. So an inner class will simply be formatted as "Enclosing$Inner".
         */
        @TruffleBoundary
        @ExportMessage
        Object getMetaSimpleName() {
            String qualifiedName = stackTraceElement.getClassName();
            return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        }

        @ExportMessage
        boolean isMetaInstance(Object instance) {
            return instance == HostStackTraceElementObject.this;
        }

    }
}
