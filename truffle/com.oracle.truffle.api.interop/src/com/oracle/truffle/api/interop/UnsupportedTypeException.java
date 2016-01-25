/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.interop;

import java.util.Arrays;

/**
 * An exception thrown if a {@link TruffleObject} does not support the type of one ore more
 * arguments provided by a foreign access.
 */
public class UnsupportedTypeException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private final Object[] suppliedValues;

    private UnsupportedTypeException(Object[] suppliedValues) {
        super("Unsupported types: " + Arrays.toString(suppliedValues));
        this.suppliedValues = suppliedValues;
    }

    /**
     * Returns the arguments of the foreign object access that were not supported by the
     * {@link TruffleObject}.
     *
     * @return the unsupported arguments
     */
    public Object[] getSuppliedValues() {
        return suppliedValues;
    }

    /**
     * Raises an {@link UnsupportedTypeException}, hidden as a {@link RuntimeException}, which
     * allows throwing it without an explicit throws declaration. The {@link ForeignAccess} methods
     * (e.g. <code> ForeignAccess.sendRead </code>) catch the exceptions and re-throw them as
     * checked exceptions.
     *
     * @param suppliedValues values that were not supported
     *
     * @return the exception
     */
    public static RuntimeException raise(Object[] suppliedValues) {
        return silenceException(RuntimeException.class, new UnsupportedTypeException(suppliedValues));
    }
}
