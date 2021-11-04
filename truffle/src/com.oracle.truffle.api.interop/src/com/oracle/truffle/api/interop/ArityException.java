/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * An exception thrown if a executable or instantiable object was provided with the wrong number of
 * arguments.
 *
 * @since 0.11
 */
public final class ArityException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private final int expectedMinArity;
    private final int expectedMaxArity;
    private final int actualArity;

    private ArityException(int expectedMinArity, int expectedMaxArity, int actualArity, Throwable cause) {
        super(null, cause);
        this.expectedMinArity = expectedMinArity;
        this.expectedMaxArity = expectedMaxArity;
        this.actualArity = actualArity;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    @TruffleBoundary
    public String getMessage() {
        String given;
        if (actualArity < 0) {
            given = "unknown";
        } else {
            given = String.valueOf(actualArity);
        }
        String expected;
        if (expectedMinArity == expectedMaxArity) {
            expected = String.valueOf(expectedMinArity);
        } else {
            if (expectedMaxArity < 0) {
                expected = expectedMinArity + "+";
            } else {
                expected = expectedMinArity + "-" + expectedMaxArity;
            }
        }
        return String.format("Arity error - expected: %s actual: %s", expected, given);
    }

    /**
     * Returns the minimum number of arguments that are expected. The returned minimum arity might
     * be less conservative than the actual specification of the executable or instantiable invoked.
     *
     * @since 21.0
     */
    public int getExpectedMinArity() {
        return expectedMinArity;
    }

    /**
     * Returns the maximum number of arguments that are expected. Returns a negative number if
     * infinite arguments can be provided.
     *
     * @since 21.0
     */
    public int getExpectedMaxArity() {
        return expectedMaxArity;
    }

    /**
     * Returns the actual number of arguments provided by the foreign access. If a negative value is
     * returned then the actual arity is unknown.
     *
     * @return the number of provided arguments
     * @since 0.11
     */
    public int getActualArity() {
        return actualArity;
    }

    /**
     * Creates an {@link ArityException} to indicate that the wrong number of arguments were
     * provided. Throws an {@link IllegalArgumentException} if the arguments are invalid and
     * assertions (-ea) are enabled.
     * <p>
     * This method is designed to be used in {@link CompilerDirectives#inCompiledCode() compiled}
     * code paths.
     *
     * @param expectedMinArity (inclusive) the minimum number of arguments expected by the
     *            executable. Must be greater or equal to zero.
     * @param expectedMaxArity (inclusive) the maximum number of arguments expected by the
     *            executable. If the maximum is negative then an infinite number of arguments is
     *            expected. If the number is positive then the maximum must be greater or equal to
     *            {@code expectedMinArity}.
     * @param actualArity the number of provided by the executable. The actual arity must not be
     *            within range of the expected min and max arity.
     * @since 21.2
     */
    public static ArityException create(int expectedMinArity, int expectedMaxArity, int actualArity) {
        assert validateArity(expectedMinArity, expectedMaxArity, actualArity);
        return new ArityException(expectedMinArity, expectedMaxArity, actualArity, null);
    }

    /**
     * Creates an {@link ArityException} to indicate that the wrong number of arguments were
     * provided. Throws an {@link IllegalArgumentException} if the arguments are invalid and
     * assertions (-ea) are enabled.
     * <p>
     * In addition a cause may be provided. The cause should only be set if the guest language code
     * caused this problem. An example for this is a language specific proxy mechanism that invokes
     * guest language code to describe an object. If the guest language code fails to execute and
     * this interop exception is a valid interpretation of the error, then the error should be
     * provided as cause. The cause can then be used by the source language as new exception cause
     * if the {@link InteropException} is translated to a source language error. If the
     * {@link InteropException} is discarded, then the cause will most likely get discarded by the
     * source language as well. Note that the cause must be of type
     * {@link com.oracle.truffle.api.TruffleException} in addition to {@link Throwable} otherwise an
     * {@link IllegalArgumentException} is thrown.
     * <p>
     * This method is designed to be used in {@link CompilerDirectives#inCompiledCode() compiled}
     * code paths.
     *
     * @param expectedMinArity (inclusive) the minimum number of arguments expected by the
     *            executable. Must be greater or equal to zero.
     * @param expectedMaxArity (inclusive) the maximum number of arguments expected by the
     *            executable. If the maximum is negative then an infinite number of arguments is
     *            expected. If the number is positive then the maximum must be greater or equal to
     *            {@code expectedMinArity}.
     * @param actualArity the number of provided by the executable. The actual arity must not be
     *            within range of the expected min and max arity.
     * @param cause the guest language exception that caused the error.
     * @since 21.2
     */
    public static ArityException create(int expectedMinArity, int expectedMaxArity, int actualArity, Throwable cause) {
        assert validateArity(expectedMinArity, expectedMaxArity, actualArity);
        return new ArityException(expectedMinArity, expectedMaxArity, actualArity, cause);
    }

    private static boolean validateArity(int expectedMinArity, int expectedMaxArity, int actualArity) {
        if (expectedMinArity < 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Expected min arity must be greater or equal to zero.");
        } else if (expectedMaxArity >= 0) {
            if (expectedMaxArity < expectedMinArity) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("Expected max arity must be greater or equal to min arity.");
            }
            if (actualArity >= 0 && actualArity >= expectedMinArity && actualArity <= expectedMaxArity) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("Actual arity is in valid arity range.");
            }
        } else {
            if (actualArity >= 0 && actualArity >= expectedMinArity) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("Actual arity is in valid arity range.");
            }
        }
        return true;
    }

}
