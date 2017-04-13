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

import com.oracle.truffle.api.CompilerDirectives;

/**
 * An exception thrown if a foreign access tries to access a property of a {@link TruffleObject}
 * that is not accessible.
 *
 * @since 0.11
 */
public final class UnknownIdentifierException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private final String unknownIdentifier;

    private UnknownIdentifierException(String unknownIdentifier) {
        super("Unknown identifier: " + unknownIdentifier);
        this.unknownIdentifier = unknownIdentifier;
    }

    /**
     * Returns the identifier that could not be accessed.
     *
     * @return the unaccessible identifier
     * @since 0.11
     */
    public String getUnknownIdentifier() {
        return unknownIdentifier;
    }

    /**
     * Raises an {@link UnknownIdentifierException}, hidden as a {@link RuntimeException}, which
     * allows throwing it without an explicit throws declaration. The {@link ForeignAccess} methods
     * (e.g. <code> ForeignAccess.sendRead </code>) catch the exceptions and re-throw them as
     * checked exceptions.
     *
     * @param unknownIdentifier the identifier that could not be accessed
     *
     * @return the exception
     * @since 0.11
     */
    public static RuntimeException raise(String unknownIdentifier) {
        CompilerDirectives.transferToInterpreter();
        return silenceException(RuntimeException.class, new UnknownIdentifierException(unknownIdentifier));
    }

}
