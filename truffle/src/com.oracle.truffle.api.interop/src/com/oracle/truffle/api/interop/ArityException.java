/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * An exception thrown if a foreign function or method invocation provides the wrong number of
 * arguments.
 *
 * @since 0.11
 */
public final class ArityException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private final int expectedArity;
    private final int actualArity;

    private ArityException(int expectedArity, int actualArity) {
        super("Arity error - expected: " + expectedArity + " actual: " + actualArity);
        this.expectedArity = expectedArity;
        this.actualArity = actualArity;
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
     * Raises an {@link ArityException}, hidden as a {@link RuntimeException}, which allows throwing
     * it without an explicit throws declaration. The {@link ForeignAccess} methods (e.g.
     * <code> ForeignAccess.sendRead </code>) catch the exceptions and re-throw them as checked
     * exceptions.
     *
     * @param expectedArity the number of arguments expected by the foreign object
     * @param actualArity the number of provided by the foreign access
     *
     * @return the exception
     * @since 0.11
     */
    public static RuntimeException raise(int expectedArity, int actualArity) {
        CompilerDirectives.transferToInterpreter();
        return silenceException(RuntimeException.class, new ArityException(expectedArity, actualArity));
    }

}
