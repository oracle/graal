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
 * The handle of a native foreign function. Use a {@code NativeFunctionHandle} to invoke native
 * target functions.
 * <p>
 * The user of a {@code NativeFunctionHandle} has to pack the boxed arguments into an
 * {@code Object[]} according to the ABI of the target platform (e.g. Unix AMD64 system:
 * {@link "http://www.uclibc.org/docs/psABI-x86_64.pdf"}). The {@code NativeFunctionHandle} unboxes
 * the arguments and places them into the right location (register / stack) according to the ABI.
 * <p>
 * A {@code NativeFunctionHandle} returns the boxed return value of the native target function. The
 * boxed value is the return value as specified by the ABI.
 */
public interface NativeFunctionHandle {

    /**
     * Calls the native foreign function.
     * 
     * @param args the arguments that will be passed to the native foreign function
     */
    Object call(Object[] args);

    /**
     * Returns the installed code of the call stub for the native foreign function call.
     * 
     * @return the installed code of the native call stub
     */
    InstalledCode getCallStub();
}
