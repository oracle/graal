/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import java.util.Objects;

import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;

/**
 * Represents an exception handler within the bytecodes.
 */
public final class ExceptionHandler {

    public static final ExceptionHandler[] EMPTY_ARRAY = new ExceptionHandler[0];

    private final char startBCI;
    private final char endBCI;
    private final char handlerBCI;
    private final char catchTypeCPI;
    private final Symbol<Type> catchType;

    /**
     * Creates a new exception handler with the specified ranges.
     *
     * @param startBCI the start index of the protected range
     * @param endBCI the end index of the protected range
     * @param catchBCI the index of the handler
     * @param catchTypeCPI the index of the throwable class in the constant pool
     * @param catchType the type caught by this exception handler
     */
    public ExceptionHandler(int startBCI, int endBCI, int catchBCI, int catchTypeCPI, Symbol<Type> catchType) {
        this.startBCI = (char) startBCI;
        this.endBCI = (char) endBCI;
        this.handlerBCI = (char) catchBCI;
        this.catchTypeCPI = (char) catchTypeCPI;
        this.catchType = catchType;
    }

    /**
     * Returns the start bytecode index of the protected range of this handler.
     */
    public int getStartBCI() {
        return startBCI;
    }

    /**
     * Returns the end bytecode index of the protected range of this handler.
     */
    public int getEndBCI() {
        return endBCI;
    }

    /**
     * Returns the bytecode index of the handler block of this handler.
     */
    public int getHandlerBCI() {
        return handlerBCI;
    }

    /**
     * Returns the index into the constant pool representing the type of exception caught by this
     * handler.
     */
    public int catchTypeCPI() {
        return catchTypeCPI;
    }

    /**
     * Checks whether this handler catches all exceptions.
     *
     * @return {@code true} if this handler catches all exceptions
     */
    public boolean isCatchAll() {
        return catchTypeCPI == 0;
    }

    /**
     * Returns whether the given bci is covered by this exception handler. Equivalent to
     * {@code getStartBCI() <= bci && bci < getEndBCI()}
     */
    public boolean covers(int bci) {
        return startBCI <= bci && bci < endBCI;
    }

    /**
     * Returns the type of exception caught by this exception handler.
     */
    public Symbol<Type> getCatchType() {
        return catchType;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ExceptionHandler)) {
            return false;
        }
        ExceptionHandler that = (ExceptionHandler) obj;
        if (this.startBCI != that.startBCI || this.endBCI != that.endBCI || this.handlerBCI != that.handlerBCI || this.catchTypeCPI != that.catchTypeCPI) {
            return false;
        }
        return Objects.equals(this.catchType, that.catchType);
    }

    @Override
    public String toString() {
        return "ExceptionHandler<startBCI=" + getStartBCI() + ", endBCI=" + getEndBCI() + ", handlerBCI=" + getHandlerBCI() + ", catchTypeCPI=" + getCatchType() + ", catchType=" + getCatchType() +
                        ">";
    }

    @Override
    public int hashCode() {
        return catchTypeCPI ^ endBCI ^ handlerBCI;
    }
}
