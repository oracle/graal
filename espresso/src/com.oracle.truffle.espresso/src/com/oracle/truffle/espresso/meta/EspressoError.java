/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.meta;

import java.util.ArrayList;
import java.util.Locale;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Indicates a condition in Espresso related code that should never occur during normal operation.
 * 
 * These methods cannot be reachable for compilation, they must be called behind a
 * {@link TruffleBoundary} or after an
 * {@link CompilerDirectives#transferToInterpreterAndInvalidate() invalidating deopt}.
 */
public final class EspressoError extends Error {

    static final String UNREACHABLE_MESSAGE = "EspressoError.* host exception is reachable.\n" +
                    "Throw it behind a @TruffleBoundary or deopt with CompilerDirectives.transferToInterpreterAndInvalidate();";

    private static final long serialVersionUID = 2625263796982958128L;

    public static RuntimeException unimplemented() {
        CompilerAsserts.neverPartOfCompilation(UNREACHABLE_MESSAGE);
        throw new EspressoError("unimplemented");
    }

    public static RuntimeException unimplemented(String message) {
        CompilerAsserts.neverPartOfCompilation(UNREACHABLE_MESSAGE);
        throw new EspressoError("unimplemented: " + message);
    }

    public static RuntimeException fatal(String message) {
        CompilerAsserts.neverPartOfCompilation(UNREACHABLE_MESSAGE);
        throw new EspressoError("fatal: " + message);
    }

    public static RuntimeException shouldNotReachHere() {
        CompilerAsserts.neverPartOfCompilation(UNREACHABLE_MESSAGE);
        throw new EspressoError("should not reach here");
    }

    public static RuntimeException shouldNotReachHere(String message) {
        CompilerAsserts.neverPartOfCompilation(UNREACHABLE_MESSAGE);
        throw new EspressoError("should not reach here: " + message);
    }

    public static RuntimeException shouldNotReachHere(String message, Throwable cause) {
        CompilerAsserts.neverPartOfCompilation(UNREACHABLE_MESSAGE);
        throw new EspressoError("should not reach here: " + message, cause);
    }

    public static RuntimeException shouldNotReachHere(Throwable cause) {
        CompilerAsserts.neverPartOfCompilation(UNREACHABLE_MESSAGE);
        throw new EspressoError("should not reach here", cause);
    }

    public static RuntimeException fatal(Object... msg) {
        CompilerAsserts.neverPartOfCompilation();
        throw new EspressoError("fatal: " + cat(msg));
    }

    @TruffleBoundary
    public static String cat(Object... strs) {
        StringBuilder res = new StringBuilder();
        for (Object str : strs) {
            res.append(str);
        }
        return res.toString();
    }

    /**
     * Checks a given condition and throws a {@link EspressoError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param message the message that will be associated with the error
     */
    public static void guarantee(boolean condition, String message) {
        if (!condition) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new EspressoError("failed guarantee: " + message);
        }
    }

    /**
     * Checks a given condition and throws a {@link EspressoError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param message the message that will be associated with the error
     * @param additionalContext appended to the exception message that condition doesn't hold
     */
    public static void guarantee(boolean condition, String message, Object additionalContext) {
        if (!condition) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new EspressoError("failed guarantee: " + message + " " + additionalContext);
        }
    }

    /**
     * This constructor creates a {@link EspressoError} with a given message.
     *
     * @param message the message that will be associated with the error
     */
    private EspressoError(String message) {
        super(message);
    }

    /**
     * This constructor creates a {@link EspressoError} with a given message and a given causing
     * Throwable instance.
     *
     * @param message the message that will be associated with the error
     * @param cause the original exception that contains additional information on this error
     */
    private EspressoError(String message, Throwable cause) {
        super(message, cause);
    }

    @TruffleBoundary
    public static String format(String msg, Object... args) {
        if (args != null) {
            // expand Iterable parameters into a list representation
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Iterable<?>) {
                    ArrayList<Object> list = new ArrayList<>();
                    for (Object o : (Iterable<?>) args[i]) {
                        list.add(o);
                    }
                    args[i] = list.toString();
                }
            }
        }
        return String.format(Locale.ENGLISH, msg, args);
    }
}
