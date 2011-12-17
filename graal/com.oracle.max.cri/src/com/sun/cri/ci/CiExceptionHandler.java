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
package com.sun.cri.ci;

import com.sun.cri.ri.*;

/**
 * An implementation of the {@link RiExceptionHandler} interface.
 */
public class CiExceptionHandler implements RiExceptionHandler {

    public static final CiExceptionHandler[] NONE = {};

    public final int startBCI;
    public final int endBCI;
    public final int handlerBCI;
    public final int catchTypeCPI;
    public final RiType catchType;

    /**
     * Creates a new exception handler with the specified ranges.
     * @param startBCI the start index of the protected range
     * @param endBCI the end index of the protected range
     * @param catchBCI the index of the handler
     * @param catchTypeCPI the index of the throwable class in the constant pool
     * @param catchType the type caught by this exception handler
     */
    public CiExceptionHandler(int startBCI, int endBCI, int catchBCI, int catchTypeCPI, RiType catchType) {
        this.startBCI = startBCI;
        this.endBCI = endBCI;
        this.handlerBCI = catchBCI;
        this.catchTypeCPI = catchTypeCPI;
        this.catchType = catchType;
    }

    public int startBCI() {
        return startBCI;
    }

    public int endBCI() {
        return endBCI;
    }

    public int handlerBCI() {
        return handlerBCI;
    }

    public int catchTypeCPI() {
        return catchTypeCPI;
    }

    public boolean isCatchAll() {
        return catchTypeCPI == 0;
    }

    public RiType catchType() {
        return catchType;
    }

    @Override
    public String toString() {
        return new StringBuilder(20).
            append('[').
            append(startBCI).
            append(" - ").
            append(endBCI).
            append(") -> ").
            append(handlerBCI).
            append(" type=").
            append(catchType == null ? "*any*" : catchType.name()).
            toString();
    }
}
