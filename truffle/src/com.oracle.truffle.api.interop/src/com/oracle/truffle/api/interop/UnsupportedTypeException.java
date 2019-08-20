/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * An exception thrown if a {@link TruffleObject} does not support the type of one ore more
 * arguments.
 *
 * @since 0.11
 */
public final class UnsupportedTypeException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private final Object[] suppliedValues;

    private UnsupportedTypeException(Exception cause, Object[] suppliedValues) {
        super(cause);
        this.suppliedValues = suppliedValues;
    }

    private UnsupportedTypeException(String message, Object[] suppliedValues) {
        super(message);
        this.suppliedValues = suppliedValues;
    }

    /**
     * Returns the arguments of the foreign object access that were not supported by the
     * {@link TruffleObject}.
     *
     * @return the unsupported arguments
     * @since 0.11
     */
    public Object[] getSuppliedValues() {
        return suppliedValues;
    }

    /**
     * @since 0.11
     * @deprecated use {@link #create(Object[])} instead. Interop exceptions should directly be
     *             thrown and no longer be hidden as runtime exceptions.
     */
    @Deprecated
    public static RuntimeException raise(Object[] suppliedValues) {
        return raise(null, suppliedValues);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #create(Object[])} instead. Interop exceptions should directly be
     *             thrown and no longer be hidden as runtime exceptions.
     */
    @Deprecated
    public static RuntimeException raise(Exception cause, Object[] suppliedValues) {
        CompilerDirectives.transferToInterpreter();
        return silenceException(RuntimeException.class, new UnsupportedTypeException(cause, suppliedValues));
    }

    /**
     * Creates an {@link UnsupportedTypeException} to indicate that an argument type is not
     * supported.
     *
     * @since 19.0
     */
    @TruffleBoundary
    public static UnsupportedTypeException create(Object[] suppliedValues) {
        CompilerDirectives.transferToInterpreter();
        return new UnsupportedTypeException((String) null, suppliedValues);
    }

    /**
     * Creates an {@link UnsupportedTypeException} to indicate that an argument type is not
     * supported.
     *
     * @since 19.0
     */
    @TruffleBoundary
    public static UnsupportedTypeException create(Object[] suppliedValues, String hint) {
        CompilerDirectives.transferToInterpreter();
        return new UnsupportedTypeException(hint, suppliedValues);
    }

}
