/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.program;

/**
 * A collection of static methods for reporting a warning when an unexpected, non-fatal condition is encountered.
 */
public final class ProgramWarning {

    /**
     * Implemented by a client that can {@linkplain ProgramWarning#setHandler(Handler) register}
     * itself to handle program warnings instead of having them printed to {@link System#err}.
     */
    public interface Handler {

        /**
         * Handles display a given warning message.
         *
         * @param message a warning message
         */
        void handle(String message);
    }

    /**
     * Registers a handler to which warnings are redirected. Any previously registered handler
     * is overwritten and discarded.
     *
     * @param h if non-null, this object's {@link Handler#handle(String)} method is messaged instead of
     *            printing the warning to {@link System#err} a ProgramError.
     */
    public static void setHandler(Handler h) {
        handler = h;
    }

    private static Handler handler;

    private ProgramWarning() {
    }

    /**
     * Prints a given warning message.
     *
     * @param warning the warning message to print
     */
    public static void message(String warning) {
        if (handler != null) {
            handler.handle(warning);
        } else {
            System.err.println("WARNING: " + warning);
        }
    }

    /**
     * Checks a given condition and if it's {@code false}, the appropriate warning message is printed.
     *
     * @param condition a condition to test
     * @param message the warning message to be printed if {@code condition == false}
     */
    public static void check(boolean condition, String warning) {
        if (!condition) {
            message(warning);
        }
    }
}
