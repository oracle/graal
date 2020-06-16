/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * An exception thrown to enter a slow path. The Truffle optimizer has special knowledge of this
 * exception class and will never compile a catch block that catches this exception type.
 *
 * @since 0.8 or earlier
 */
public class SlowPathException extends Exception {

    private static final long serialVersionUID = 3676602078425211386L;

    /**
     * Creates an exception thrown to enter a slow path.
     *
     * @since 0.8 or earlier
     */
    public SlowPathException() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
    }

    /**
     * Creates an exception thrown to enter a slow path.
     *
     * @since 0.8 or earlier
     */
    public SlowPathException(String message, Throwable cause) {
        super(message, cause);
        CompilerDirectives.transferToInterpreterAndInvalidate();
    }

    /**
     * Creates an exception thrown to enter a slow path.
     *
     * @since 0.8 or earlier
     */
    public SlowPathException(String message) {
        super(message);
        CompilerDirectives.transferToInterpreterAndInvalidate();
    }

    /**
     * Creates an exception thrown to enter a slow path.
     *
     * @since 0.8 or earlier
     */
    public SlowPathException(Throwable cause) {
        super(cause);
        CompilerDirectives.transferToInterpreterAndInvalidate();
    }

    /**
     * For performance reasons, this exception does not record any stack trace information.
     *
     * @deprecated the method will become final in future releases.
     *
     * @since 0.8 or earlier
     */
    @Deprecated
    @SuppressWarnings("sync-override")
    @Override
    public final Throwable fillInStackTrace() {
        return this;
    }
}
