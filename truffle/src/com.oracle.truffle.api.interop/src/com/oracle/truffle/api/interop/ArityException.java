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

/**
 * An exception thrown if a executable or instantiable object was provided with the wrong number of
 * arguments.
 *
 * @since 0.11
 */
public final class ArityException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private final int expectedArity;
    private final int actualArity;

    private ArityException(int expectedArity, int actualArity) {
        this.expectedArity = expectedArity;
        this.actualArity = actualArity;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String getMessage() {
        return "Arity error - expected: " + expectedArity + " actual: " + actualArity;
    }

    /**
     * Returns the number of arguments that the foreign object expects.
     *
     * @return the number of expected arguments
     * @since 0.11
     */
    public int getExpectedArity() {
        return expectedArity;
    }

    /**
     * Returns the actual number of arguments provided by the foreign access.
     *
     * @return the number of provided arguments
     * @since 0.11
     */
    public int getActualArity() {
        return actualArity;
    }

    /**
     * Creates an {@link ArityException} to indicate that the wrong number of arguments were
     * provided.
     *
     * @param expectedArity the number of arguments expected by the foreign object
     * @param actualArity the number of provided by the foreign access
     * @since 19.0
     */
    public static ArityException create(int expectedArity, int actualArity) {
        CompilerDirectives.transferToInterpreter();
        return new ArityException(expectedArity, actualArity);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #create(int, int)} instead. Interop exceptions should directly be
     *             thrown and no longer be hidden as runtime exceptions.
     */
    @Deprecated
    public static RuntimeException raise(int expectedArity, int actualArity) {
        CompilerDirectives.transferToInterpreter();
        return silenceException(RuntimeException.class, new ArityException(expectedArity, actualArity));
    }

}
