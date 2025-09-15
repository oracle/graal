/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.vmaccess;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Exception thrown when a method invoked through {@link VMAccess#invoke} throws an exception.
 * <p>
 * The thrown exception can be retrieved with {@link #getExceptionObject()}.
 */
@SuppressWarnings("serial")
public class InvocationException extends RuntimeException {
    private final JavaConstant exceptionObject;

    /**
     * Constructs an {@link InvocationException} for the given exception object.
     *
     * @param exceptionObject a {@link JavaConstant} representing a non-null exception object.
     */
    public InvocationException(JavaConstant exceptionObject) {
        if (exceptionObject.isNull() || !exceptionObject.getJavaKind().isObject()) {
            throw new IllegalArgumentException("The exception object must be a non-null object");
        }
        this.exceptionObject = exceptionObject;
    }

    /**
     * Constructs an {@link InvocationException} for the given exception object and cause.
     *
     * @param exceptionObject a {@link JavaConstant} representing a non-null exception object.
     * @param cause an exception that was involved in the exception handling of
     *            {@code exceptionObject}.
     */
    public InvocationException(JavaConstant exceptionObject, Throwable cause) {
        super(cause);
        if (exceptionObject.isNull() || !exceptionObject.getJavaKind().isObject()) {
            throw new IllegalArgumentException("The exception object must be a non-null object");
        }
        this.exceptionObject = exceptionObject;
    }

    /**
     * Returns a {@link JavaConstant} representing the exception object that was thrown.
     */
    public JavaConstant getExceptionObject() {
        return exceptionObject;
    }
}
