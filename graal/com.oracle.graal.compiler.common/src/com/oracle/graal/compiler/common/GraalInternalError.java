/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common;

import java.util.*;

/**
 * This error represents a conditions that should never occur during normal operation.
 */
public class GraalInternalError extends Error {

    private static final long serialVersionUID = 8776065085829593278L;
    private final ArrayList<String> context = new ArrayList<>();

    public static RuntimeException unimplemented() {
        throw new GraalInternalError("unimplemented");
    }

    public static RuntimeException unimplemented(String msg) {
        throw new GraalInternalError("unimplemented: %s", msg);
    }

    public static RuntimeException shouldNotReachHere() {
        throw new GraalInternalError("should not reach here");
    }

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new GraalInternalError("should not reach here: %s", msg);
    }

    /**
     * Checks a given condition and throws a {@link GraalInternalError} if it is false. Guarantees
     * are stronger than assertions in that they are always checked. Error messages for guarantee
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
            throw new GraalInternalError("failed guarantee: " + msg, args);
        }
    }

    /**
     * This constructor creates a {@link GraalInternalError} with a message assembled via
     * {@link String#format(String, Object...)}. It always uses the ENGLISH locale in order to
     * always generate the same output.
     *
     * @param msg the message that will be associated with the error, in String.format syntax
     * @param args parameters to String.format - parameters that implement {@link Iterable} will be
     *            expanded into a [x, x, ...] representation.
     */
    public GraalInternalError(String msg, Object... args) {
        super(format(msg, args));
    }

    /**
     * This constructor creates a {@link GraalInternalError} for a given causing Throwable instance.
     *
     * @param cause the original exception that contains additional information on this error
     */
    public GraalInternalError(Throwable cause) {
        super(cause);
    }

    /**
     * This constructor creates a {@link GraalInternalError} from a given GraalInternalError
     * instance.
     *
     * @param e the original GraalInternalError
     */
    public GraalInternalError(GraalInternalError e) {
        super(e.getCause());
        context.addAll(e.context);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(super.toString());
        for (String s : context) {
            str.append("\n\tat ").append(s);
        }
        return str.toString();
    }

    private static String format(String msg, Object... args) {
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

    public GraalInternalError addContext(String newContext) {
        this.context.add(newContext);
        return this;
    }

    public GraalInternalError addContext(String name, Object obj) {
        return addContext(format("%s: %s", name, obj));
    }

}
