/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.debug;

/**
 * Represents an indentation level for logging.
 * <p>
 * Note that calling the logging/indent/outdent methods of this interface updates the last used
 * Indent of the current DebugScope. If no instance of Indent is available (e.g. at the beginning of
 * a method), then the corresponding static methods of the Debug class should be used. They perform
 * logging and indentation based on the last used Indent of the current DebugScope.
 * <p>
 * Example usage:
 * 
 * <pre>
 *      void method() {
 *      
 *          Indent in = Debug.logIndent("header message");
 *          ...
 *          in.log("message");
 *          ...
 *          Indent in2 = in.logIndent("sub-header message");
 *          ...
 *          {
 *              in2.log("inner message");
 *          }
 *          ...
 *          in.outdent();
 *      }
 * </pre>
 */
public interface Indent {

    /**
     * Prints an indented message to the DebugLevel's logging stream if logging is enabled.
     * 
     * @param msg The format string of the log message
     * @param args The arguments referenced by the log message string
     * @see Debug#log
     */
    void log(String msg, Object... args);

    /**
     * Turns on/off logging for this indentation level.
     * 
     * @param enabled If true, logging is enabled, otherwise disabled
     */
    void setEnabled(boolean enabled);

    /**
     * Creates a new indentation level (by adding some spaces).
     * 
     * @return The new indentation level
     * @see Debug#indent
     */
    Indent indent();

    /**
     * A convenience function which combines {@link #log} and {@link #indent}.
     * 
     * @param msg The format string of the log message
     * @param args The arguments referenced by the log message string
     * @return The new indentation level
     * @see Debug#logIndent
     */
    Indent logIndent(String msg, Object... args);

    /**
     * Restores the previous indent level. Calling this method is important to restore the correct
     * last used Indent in the current DebugScope.
     * 
     * @return The indent level from which this Indent was created.
     */
    Indent outdent();
}
