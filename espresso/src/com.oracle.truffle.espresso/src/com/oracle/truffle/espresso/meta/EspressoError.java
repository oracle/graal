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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Indicates a condition in Espresso related code that should never occur during normal operation.
 */
public final class EspressoError extends Error {

    private static final long serialVersionUID = 2625263796982958128L;

    public static RuntimeException unimplemented() {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoError("unimplemented");
    }

    public static RuntimeException unimplemented(Object... msg) {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoError("unimplemented: %s", cat(msg));
    }

    public static RuntimeException shouldNotReachHere() {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoError("should not reach here");
    }

    public static RuntimeException shouldNotReachHere(Object... msg) {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoError("should not reach here: %s", cat(msg));
    }

    public static RuntimeException shouldNotReachHere(String message, Throwable cause) {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoError(message, cause);
    }

    public static RuntimeException shouldNotReachHere(Throwable cause) {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoError(cause);
    }

    public static RuntimeException unexpected(String msg, Throwable cause) {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoError(msg, cause);
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
     * @param msg the message that will be associated with the error, in
     *            {@link String#format(String, Object...)} syntax
     * @param args arguments to the format string
     */
    public static void guarantee(boolean condition, String msg, Object... args) {
        if (!condition) {
            CompilerDirectives.transferToInterpreter();
            throw new EspressoError("failed guarantee: " + msg, args);
        }
    }

    /**
     * This constructor creates a {@link EspressoError} with a given message.
     *
     * @param msg the message that will be associated with the error
     */
    public EspressoError(String msg) {
        super(msg);
    }

    /**
     * This constructor creates a {@link EspressoError} with a message assembled via
     * {@link String#format(String, Object...)}. It always uses the ENGLISH locale in order to
     * always generate the same output.
     *
     * @param msg the message that will be associated with the error, in String.format syntax
     * @param args parameters to String.format - parameters that implement {@link Iterable} will be
     *            expanded into a [x, x, ...] representation.
     */
    public EspressoError(String msg, Object... args) {
        super(format(msg, args));
    }

    /**
     * This constructor creates a {@link EspressoError} for a given causing Throwable instance.
     *
     * @param cause the original exception that contains additional information on this error
     */
    public EspressoError(Throwable cause) {
        super(cause);
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
