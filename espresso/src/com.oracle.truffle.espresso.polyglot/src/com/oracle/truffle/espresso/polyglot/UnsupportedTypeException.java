/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;

/**
 * An exception thrown if an interop {@link Object} does not support the type of one ore more
 * arguments.
 *
 * @since 21.0
 */
@SuppressWarnings("serial")
public final class UnsupportedTypeException extends InteropException {
    private final Object[] suppliedValues;

    private UnsupportedTypeException(String message, Object[] suppliedValues) {
        super(message, null);
        this.suppliedValues = suppliedValues;
    }

    private UnsupportedTypeException(String message, Object[] suppliedValues, Throwable cause) {
        super(message, cause);
        this.suppliedValues = suppliedValues;
    }

    /**
     * Returns the arguments of the foreign object access that were not supported by the foreign
     * {@link Object}.
     *
     * @return the unsupported arguments
     * @since 21.0
     */
    public Object[] getSuppliedValues() {
        return suppliedValues;
    }

    /**
     * Creates an {@link UnsupportedTypeException} to indicate that an argument type is not
     * supported.
     *
     * @since 21.0
     */
    public static UnsupportedTypeException create(Object[] suppliedValues) {
        return new UnsupportedTypeException((String) null, suppliedValues);
    }

    /**
     * Creates an {@link UnsupportedTypeException} to indicate that an argument type is not
     * supported.
     *
     * @since 21.0
     */
    public static UnsupportedTypeException create(Object[] suppliedValues, String hint) {
        return new UnsupportedTypeException(hint, suppliedValues);
    }

    /**
     * Creates an {@link UnsupportedTypeException} to indicate that an argument type is not
     * supported.
     * <p>
     * In addition a cause may be provided. The cause should only be set if the guest language code
     * caused this problem. An example for this is a language specific proxy mechanism that invokes
     * guest language code to describe an object. If the guest language code fails to execute and
     * this interop exception is a valid interpretation of the error, then the error should be
     * provided as cause.
     *
     * @param cause the guest language exception that caused the error.
     * 
     * @since 21.0
     */
    public static UnsupportedTypeException create(Object[] suppliedValues, String hint, Throwable cause) {
        return new UnsupportedTypeException(hint, suppliedValues, cause);
    }

    @SuppressWarnings({"static-method", "unused"})
    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        throw new NotSerializableException(UnsupportedTypeException.class.getSimpleName() + " serialization is not supported.");
    }
}
