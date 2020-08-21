/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleException;

/**
 * An exception thrown if a {@link TruffleObject} does not support a interop message. If this
 * exception is thrown then the receiver does not support the message at all and it is not supported
 * for any arguments given to the message.
 *
 * @since 0.11
 */
public final class UnsupportedMessageException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private UnsupportedMessageException(Throwable cause) {
        super(null, cause);
    }

    private UnsupportedMessageException() {
        super(null);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String getMessage() {
        return "Message not supported.";
    }

    /**
     * Creates an {@link UnsupportedMessageException} to indicate that an {@link InteropLibrary
     * interop} message is not supported.
     * <p>
     * This method is designed to be used in {@link CompilerDirectives#inCompiledCode() compiled}
     * code paths.
     *
     * @since 19.0
     */
    public static UnsupportedMessageException create() {
        return new UnsupportedMessageException();
    }

    /**
     * Creates an {@link UnsupportedMessageException} to indicate that an {@link InteropLibrary
     * interop} message is not supported.
     * <p>
     * In addition a cause may be provided. The cause should only be set if the guest language code
     * caused this problem. An example for this is a language specific proxy mechanism that invokes
     * guest language code to describe an object. If the guest language code fails to execute and
     * this interop exception is a valid interpretation of the error, then the error should be
     * provided as cause. The cause can then be used by the source language as new exception cause
     * if the {@link InteropException} is translated to a source language error. If the
     * {@link InteropException} is discarded, then the cause will most likely get discarded by the
     * source language as well. Note that the cause must be of type {@link TruffleException} in
     * addition to {@link Throwable} otherwise an {@link IllegalArgumentException} is thrown.
     * <p>
     * This method is designed to be used in {@link CompilerDirectives#inCompiledCode() compiled}
     * code paths.
     *
     * @param cause the guest language exception that caused the error.
     * @since 20.2
     */
    public static UnsupportedMessageException create(Throwable cause) {
        return new UnsupportedMessageException(cause);
    }

}
