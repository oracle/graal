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
 * The library handle of the native library.
 * <p>
 * The {@code NativeFunctionInterface} can use a {@code NativeLibraryHandle} to look up a
 * {@code NativeFunctionPointer} or a {@code NativeFunctionHandle} in this library.
 */
public interface NativeLibraryHandle {

    /**
     * Returns whether the handle is valid.
     * 
     * @return true if the handle is valid
     */
    boolean isValid();

    /**
     * Returns function pointer as raw value.
     * 
     * @return raw value of function pointer
     */
    long asRawValue();
}
