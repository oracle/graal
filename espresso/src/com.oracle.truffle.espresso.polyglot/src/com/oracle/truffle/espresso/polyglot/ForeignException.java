/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.polyglot;

/**
 * Handy wrapper for foreign exceptions.
 * 
 * <p>
 * Allows foreign exceptions to be caught in guest Java and provides seamless access to the message,
 * cause and a polyglot stacktrace containing all guest frames if any. {@link ForeignException} can
 * only catch and wrap foreign exceptions.
 *
 * <pre>
 * assert Interop.isException(foreignException);
 * try {
 *     throw Interop.throwException(foreignException);
 * } catch (ForeignException e) {
 *     log(e.getClass() + ": " + e.getMessage());
 *     ...
 *     throw e;
 * }
 * </pre>
 * 
 * The raw foreign exception object which can serve polyglot messages e.g.
 * InteropLibrary#isException(rawForeignException) can be retrieved with
 * {@link ForeignException#getForeignExceptionObject()}.
 *
 * @see Interop#throwException(Object)
 * @since 21.0
 */
public final class ForeignException extends RuntimeException {

    private static final long serialVersionUID = -765815353576751011L;

    private ForeignException() {
        throw new RuntimeException("No instance of ForeignException can be created directly");
    }

    /**
     * Returns the original wrapped foreign exception. The returned instance should be used for
     * {@link Interop} messages.
     * 
     * @return the wrapped foreign exception.
     */
    public native Object getForeignExceptionObject();

    @Override
    public String getMessage() {
        Object rawForeignObject = getForeignExceptionObject();
        assert Interop.isException(rawForeignObject);
        if (Interop.hasExceptionMessage(rawForeignObject)) {
            try {
                Object message = Interop.getExceptionMessage(rawForeignObject);
                return Interop.asString(message);
            } catch (UnsupportedMessageException e) {
                throw new AssertionError("Unexpected exception", e);
            }
        }
        return null;
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable getCause() {
        Object rawForeignObject = getForeignExceptionObject();
        assert Interop.isException(rawForeignObject);
        if (Interop.hasExceptionCause(rawForeignObject)) {
            Object cause;
            try {
                cause = Interop.getExceptionCause(rawForeignObject);
            } catch (UnsupportedMessageException e) {
                throw new AssertionError("Unexpected exception", e);
            }
            assert Interop.isException(cause);
            if (Polyglot.isForeignObject(cause)) {
                return Polyglot.cast(ForeignException.class, cause);
            } else {
                return (Throwable) cause;
            }
        }
        return null;
    }

    /**
     * Unsupported, {@link ForeignException} instances are by definition foreign and thus
     * stacktraces should not be controllable by the guest.
     */
    @Override
    public void setStackTrace(StackTraceElement[] stackTrace) {
        // Validate arguments to fulfill contract.
        for (int i = 0; i < stackTrace.length; i++) {
            if (stackTrace[i] == null) {
                throw new NullPointerException("stackTrace[" + i + "]");
            }
        }
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable initCause(Throwable cause) {
        throw new UnsupportedOperationException();
    }
}
