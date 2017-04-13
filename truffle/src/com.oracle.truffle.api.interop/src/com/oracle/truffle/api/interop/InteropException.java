/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Common super class for exceptions that can occur when sending {@link Message interop messages}.
 * This super class is used to catch any kind of these exceptions.
 *
 * @since 0.11
 */
public abstract class InteropException extends Exception {

    InteropException(String string) {
        super(string);
    }

    InteropException(Exception cause) {
        super(cause);
    }

    /**
     * Re-throw an {@link InteropException} as a {@link RuntimeException}, which allows throwing it
     * without an explicit throws declaration.
     *
     * The method returns {@link RuntimeException} so it can be used as
     * {@link com.oracle.truffle.api.dsl.test.interop.Snippets.RethrowExample} but the method never
     * returns. It throws this exception internally rather than returning any value.
     *
     * @return the exception
     * @since 0.14
     */
    public final RuntimeException raise() {
        return silenceException(RuntimeException.class, this);
    }

    @SuppressWarnings({"unchecked", "unused"})
    static <E extends Exception> RuntimeException silenceException(Class<E> type, Exception ex) throws E {
        throw (E) ex;
    }

    private static final long serialVersionUID = -5173354806966156285L;
}
