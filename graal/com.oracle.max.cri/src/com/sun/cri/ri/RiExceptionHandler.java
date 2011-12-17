/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ri;

/**
 * Represents an exception handler within the bytecode.
 */
public interface RiExceptionHandler {

    /**
     * Gets the start bytecode index of the protected range of this handler.
     * @return the start bytecode index
     */
    int startBCI();

    /**
     * Gets the end bytecode index of the protected range of this handler.
     * @return the end bytecode index
     */
    int endBCI();

    /**
     * Gets the bytecode index of the handler block of this handler.
     * @return the handler block bytecode index
     */
    int handlerBCI();

    /**
     * Gets the index into the constant pool representing the type of exception
     * caught by this handler.
     * @return the constant pool index of the catch type
     */
    int catchTypeCPI();

    /**
     * Checks whether this handler catches all exceptions.
     * @return {@code true} if this handler catches all exceptions
     */
    boolean isCatchAll();

    /**
     * The type of exception caught by this exception handler.
     *
     * @return the exception type
     */
    RiType catchType();
}
