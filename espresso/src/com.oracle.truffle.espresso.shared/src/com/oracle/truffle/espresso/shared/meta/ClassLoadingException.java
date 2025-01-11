/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.shared.meta;

import java.io.Serial;

/**
 * Indicates that an exception was thrown during class loading.
 */
public class ClassLoadingException extends Exception {
    @Serial private static final long serialVersionUID = -6396583822642157602L;

    private final boolean isClassNotFoundException;
    private final RuntimeException exception;

    public ClassLoadingException(RuntimeException e, boolean isClassNotFoundException) {
        this.isClassNotFoundException = isClassNotFoundException;
        this.exception = e;
    }

    /**
     * Whether {@link #getException()} represents this runtime's equivalent of
     * {@link ClassNotFoundException}.
     */
    public boolean isClassNotFoundException() {
        return isClassNotFoundException;
    }

    /**
     * The original exception thrown.
     */
    public RuntimeException getException() {
        return exception;

    }
}
