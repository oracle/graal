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
 * A collection of static methods for reporting errors indicating some fatal condition.
 * In the target VM, these errors can be made to perform a hard exit of the VM by
 * redirecting them via a {@linkplain #setHandler(Handler) registered} error handler.
 */

public final class ProgramError extends Error {

    /**
     * Implemented by a client that can {@linkplain ProgramError#setHandler(Handler) register}
     * itself to handle program errors instead of having them result in a {@link ProgramError}
     * instance raised.
     */
    public interface Handler {

        /**
         * Handles a given error condition. This method should never return normally.
         *
         * @param message a message describing the error condition. This value may be {@code null}
         * @param throwable an exception given more detail on the cause of the error condition. This value may be {@code null}
         */
        void handle(String message, Throwable throwable);
    }

    /**
     * Registers a handler to which error reporting is redirected. Any previously registered handler
     * is overwritten and discarded.
     *
     * @param h if non-null, this object's {@link Handler#handle(String, Throwable)} method is messaged instead of
     *            raising a ProgramError.
     */
    public static void setHandler(Handler h) {
        handler = h;
    }

    private ProgramError(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Checks a given condition and if it's {@code false}, the appropriate error handling action is taken.
     * The message reported to this action is {@code "Program Error"}.
     *
     * @param condition a condition to test
     */
    public static void check(boolean condition) {
        if (!condition) {
            throw unexpected(null, null);
        }
    }

    /**
     * Checks a given condition and if it's {@code false}, the appropriate error handling action is taken.
     *
     * @param condition a condition to test
     * @param message a message describing the error condition being tested
     */
    public static void check(boolean condition, String message) {
        if (!condition) {
            throw unexpected(message, null);
        }
    }

    /**
     * Checks a given condition and if it's {@code false}, the appropriate error handling action is taken.
     *
     * @param condition a condition to test
     * @param message a message describing the error condition being tested
     * @param object an object whose string description is to be appended to the message
     */
    public static void check(boolean condition, String message, Object object) {
        if (!condition) {
            throw unexpected(message + object.toString(), null);
        }
    }

    /**
     * Reports the occurrence of some error condition triggering the appropriate error handling action
     * to be taken. By default, this action is to raise a {@link ProgramError} exception. However,
     * if an alternative error handler has been {@linkplain #setHandler(Handler) registered}, its
     * {@link Handler#handle(String, Throwable)} method is called instead.
     *
     * This method never returns normally.
     *
     * @param message a message describing the error condition. This value may be {@code null}.
     * @param throwable an exception given more detail on the cause of the error condition. This value may be {@code null}.
     */
    public static ProgramError unexpected(String message, Throwable throwable) {
        if (handler != null) {
            handler.handle(message, throwable);
        }
        if (message == null) {
            throw new ProgramError("Unexpected Program Error:", throwable);
        }
        throw new ProgramError("Unexpected Program Error: " + message, throwable);
    }

    /**
     * Reports the occurrence of some error condition.
     *
     * This method never returns normally.
     *
     * @param message a message describing the error condition. This value may be {@code null}.
     * @see #unexpected(String, Throwable)
     */
    public static ProgramError unexpected(String message) {
        throw unexpected(message, null);
    }

    /**
     * Reports the occurrence of some error condition.
     *
     * This method never returns normally.
     *
     * @param throwable an exception given more detail on the cause of the error condition. This value may be {@code null}
     * @see #unexpected(String, Throwable)
     */
    public static ProgramError unexpected(Throwable throwable) {
        throw unexpected(null, throwable);
    }

    /**
     * Reports the occurrence of some error condition.
     *
     * This method never returns normally.
     *
     * @param throwable an exception given more detail on the cause of the error condition. This value may be {@code null}
     * @see #unexpected(String, Throwable)
     */
    public static ProgramError unexpected() {
        throw unexpected((String) null);
    }

    /**
     * Reports that a {@code switch} statement encountered a {@code case} value it was not expecting.
     *
     * This method never returns normally.
     *
     * @see #unexpected(String, Throwable)
     */
    public static ProgramError unknownCase() {
        throw unexpected("unknown switch case");
    }

    /**
     * Reports that a {@code switch} statement encountered a {@code case} value it was not expecting.
     *
     * This method never returns normally.
     *
     * @param caseValue the unexpected {@code case} value as a string
     * @see #unexpected(String, Throwable)
     */
    public static ProgramError unknownCase(String caseValue) {
        throw unexpected("unknown switch case: " + caseValue);
    }

    // Checkstyle: stop
    private static Handler handler = null;
}
