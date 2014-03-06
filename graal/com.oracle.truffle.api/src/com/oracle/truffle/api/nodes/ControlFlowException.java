/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

/**
 * An exception thrown to model control flow in a Truffle interpreter. The Truffle optimizer has
 * special knowledge of this exception class for performance optimizations.
 */
public class ControlFlowException extends RuntimeException {

    private static final long serialVersionUID = 3676602078425211386L;

    /**
     * Creates an exception thrown to model control flow.
     */
    public ControlFlowException() {
        /*
         * We use the super constructor that initializes the cause to null. Without that, the cause
         * would be this exception itself. This helps escape analysis: it avoids the circle of an
         * object pointing to itself.
         */
        super((Throwable) null);
    }

    /**
     * For performance reasons, this exception does not record any stack trace information.
     */
    @SuppressWarnings("sync-override")
    @Override
    public final Throwable fillInStackTrace() {
        return null;
    }
}
