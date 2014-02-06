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
package com.oracle.graal.api.code;

/**
 * A handle that can be used to {@linkplain #call(Object[]) call} a native function.
 */
public interface NativeFunctionHandle {

    /**
     * Calls the native function.
     * <p>
     * The caller is responsible for ensuring {@code args} comply with the platform ABI (e.g. <a
     * href="http://www.uclibc.org/docs/psABI-x86_64.pdf"> Unix AMD64 ABI</a>). If the library
     * function has struct parameters, the fields of the struct must be passed as individual
     * arguments.
     * 
     * @param args the arguments that will be passed to the native function
     * @return boxed return value of the function call
     */
    Object call(Object... args);

    /**
     * Returns the installed code of the call stub for the native function call.
     * 
     * @return the installed code of the native call stub
     */
    InstalledCode getCallStub();
}
