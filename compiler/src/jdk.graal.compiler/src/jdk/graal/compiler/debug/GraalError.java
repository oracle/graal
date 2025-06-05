/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jdk.vm.ci.code.Architecture;

/**
 * Indicates a condition that should never occur during normal operation.
 */
public class GraalError extends Error {

    private static final long serialVersionUID = 531632331813456233L;
    private final ArrayList<String> context = new ArrayList<>();

    public static RuntimeException unimplemented(String msg) {
        throw new GraalError("unimplemented: %s", msg);
    }

    public static RuntimeException unimplementedOverride() {
        throw new GraalError("unimplemented override");
    }

    public static RuntimeException unimplementedParent() {
        throw new GraalError("unimplemented method in parent class, should be overridden");
    }

    public static RuntimeException unsupportedArchitecture(Architecture arch) {
        throw new GraalError("unsupported architecture: %s", arch);
    }

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new GraalError("should not reach here: %s", msg);
    }

    public static RuntimeException shouldNotReachHereUnexpectedValue(Object obj) {
        throw new GraalError("should not reach here: unexpected value: %s", obj);
    }

    public static RuntimeException shouldNotReachHere(Throwable cause) {
        throw new GraalError(cause);
    }

    public static RuntimeException shouldNotReachHere(Throwable cause, String msg) {
        throw new GraalError(cause, "should not reach here: %s", msg);
    }

    /**
     * Checks a given condition and throws a {@link GraalError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param msg the message that will be associated with the error
     */
    public static void guarantee(boolean condition, String msg) {
        if (!condition) {
            throw new GraalError("failed guarantee: " + msg);
        }
    }

    /**
     * Checks a given condition and throws a {@link GraalError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param msg the message that will be associated with the error, in
     *            {@link String#format(String, Object...)} syntax
     * @param arg argument to the format string in {@code msg}
     */
    public static void guarantee(boolean condition, String msg, Object arg) {
        if (!condition) {
            throw new GraalError("failed guarantee: " + msg, arg);
        }
    }

    /**
     * Checks a given condition and throws a {@link GraalError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param msg the message that will be associated with the error, in
     *            {@link String#format(String, Object...)} syntax
     * @param arg1 argument to the format string in {@code msg}
     * @param arg2 argument to the format string in {@code msg}
     */
    public static void guarantee(boolean condition, String msg, Object arg1, Object arg2) {
        if (!condition) {
            throw new GraalError("failed guarantee: " + msg, arg1, arg2);
        }
    }

    /**
     * Checks a given condition and throws a {@link GraalError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param msg the message that will be associated with the error, in
     *            {@link String#format(String, Object...)} syntax
     * @param arg1 argument to the format string in {@code msg}
     * @param arg2 argument to the format string in {@code msg}
     * @param arg3 argument to the format string in {@code msg}
     */
    public static void guarantee(boolean condition, String msg, Object arg1, Object arg2, Object arg3) {
        if (!condition) {
            throw new GraalError("failed guarantee: " + msg, arg1, arg2, arg3);
        }
    }

    /**
     * Checks a given condition and throws a {@link GraalError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param msg the message that will be associated with the error, in
     *            {@link String#format(String, Object...)} syntax
     * @param arg1 argument to the format string in {@code msg}
     * @param arg2 argument to the format string in {@code msg}
     * @param arg3 argument to the format string in {@code msg}
     * @param arg4 argument to the format string in {@code msg}
     */
    public static void guarantee(boolean condition, String msg, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (!condition) {
            throw new GraalError("failed guarantee: " + msg, arg1, arg2, arg3, arg4);
        }
    }

    /**
     * Checks a given condition and throws a {@link GraalError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param msg the message that will be associated with the error, in
     *            {@link String#format(String, Object...)} syntax
     * @param arg1 argument to the format string in {@code msg}
     * @param arg2 argument to the format string in {@code msg}
     * @param arg3 argument to the format string in {@code msg}
     * @param arg4 argument to the format string in {@code msg}
     * @param arg5 argument to the format string in {@code msg}
     */
    public static void guarantee(boolean condition, String msg, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (!condition) {
            throw new GraalError("failed guarantee: " + msg, arg1, arg2, arg3, arg4, arg5);
        }
    }

    /**
     * Checks a given condition and throws a {@link GraalError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param msg the message that will be associated with the error, in
     *            {@link String#format(String, Object...)} syntax
     * @param arg1 argument to the format string in {@code msg}
     * @param arg2 argument to the format string in {@code msg}
     * @param arg3 argument to the format string in {@code msg}
     * @param arg4 argument to the format string in {@code msg}
     * @param arg5 argument to the format string in {@code msg}
     * @param arg6 argument to the format string in {@code msg}
     */
    public static void guarantee(boolean condition, String msg, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        if (!condition) {
            throw new GraalError("failed guarantee: " + msg, arg1, arg2, arg3, arg4, arg5, arg6);
        }
    }

    /**
     * Checks a given condition and throws a {@link GraalError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param msg the message that will be associated with the error, in
     *            {@link String#format(String, Object...)} syntax
     * @param arg1 argument to the format string in {@code msg}
     * @param arg2 argument to the format string in {@code msg}
     * @param arg3 argument to the format string in {@code msg}
     * @param arg4 argument to the format string in {@code msg}
     * @param arg5 argument to the format string in {@code msg}
     * @param arg6 argument to the format string in {@code msg}
     * @param arg7 argument to the format string in {@code msg}
     */
    public static void guarantee(boolean condition, String msg, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        if (!condition) {
            throw new GraalError("failed guarantee: " + msg, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        }
    }

    /**
     * This override exists to catch cases when {@link #guarantee(boolean, String, Object)} is
     * called with one argument bound to a varargs method parameter. It will bind to this method
     * instead of the single arg variant and produce a deprecation warning instead of silently
     * wrapping the Object[] inside of another Object[].
     */
    @Deprecated
    public static void guarantee(boolean condition, String msg, Object... args) {
        if (!condition) {
            throw new GraalError("failed guarantee: " + msg, args);
        }
    }

    /**
     * This constructor creates a {@link GraalError} with a given message.
     *
     * @param msg the message that will be associated with the error
     */
    public GraalError(String msg) {
        super(msg);
    }

    /**
     * This constructor creates a {@link GraalError} with a message assembled via
     * {@link String#format(String, Object...)}. It always uses the ENGLISH locale in order to
     * always generate the same output.
     *
     * @param msg the message that will be associated with the error, in String.format syntax
     * @param args parameters to String.format - parameters that implement {@link Iterable} will be
     *            expanded into a [x, x, ...] representation.
     */
    @SuppressWarnings("this-escape")
    public GraalError(String msg, Object... args) {
        super(format(msg, args));
        potentiallyAddContext(args);
    }

    /**
     * This constructor creates a {@link GraalError} for a given causing Throwable instance.
     *
     * @param cause the original exception that contains additional information on this error
     */
    public GraalError(Throwable cause) {
        super(cause);
    }

    /**
     * This constructor creates a {@link GraalError} for a given causing Throwable instance with
     * detailed error message.
     */
    @SuppressWarnings("this-escape")
    public GraalError(Throwable cause, String msg, Object... args) {
        super(format(msg, args), cause);
        potentiallyAddContext(args);
    }

    private void potentiallyAddContext(Object[] args) {
        // be pessimistic here and ensure better reporting never causes a follow up error that is
        // unhandled
        try {
            if (args != null) {
                List<String> betterContext = new ArrayList<>();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Iterable<?>) {
                        for (Object o : (Iterable<?>) args[i]) {
                            String potentialBetterString = potentialBetterString(o);
                            if (potentialBetterString != null) {
                                betterContext.add(potentialBetterString);
                            }
                        }
                    } else {
                        String potentialBetterString = potentialBetterString(args[i]);
                        if (potentialBetterString != null) {
                            betterContext.add(potentialBetterString);
                        }
                    }
                }
                if (!betterContext.isEmpty()) {
                    addContext(Arrays.toString(betterContext.toArray()));
                }
            }
        } catch (Throwable t) {
            addContext("Intercepted error in potentiallyAddContext: " + t);
        }
    }

    private static String potentialBetterString(Object o) {
        if (o == null) {
            return null;
        }
        Object potentialBetterString = Assertions.decorateObjectErrorContext(o);
        if (!potentialBetterString.toString().equals(o.toString())) {
            return potentialBetterString.toString();
        }
        return null;
    }

    /**
     * This constructor creates a {@link GraalError} and adds all the
     * {@linkplain #addContext(String) context} of another {@link GraalError}.
     *
     * @param e the original {@link GraalError}
     */
    public GraalError(GraalError e) {
        super(e);
        context.addAll(e.context);
    }

    public static GraalError asGraalError(Throwable t) {
        if (t instanceof GraalError g) {
            return g;
        }
        return new GraalError(t);
    }

    @Override
    public String toString() {
        return super.toString() + context();
    }

    public String context() {
        StringBuilder str = new StringBuilder();
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

    public GraalError addContext(String newContext) {
        this.context.add(newContext);
        return this;
    }

    public GraalError addContext(String name, Object obj) {
        return addContext(format("%s: %s", name, obj));
    }
}
