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
 * Interface to resolve a {@code NativeFunctionHandle} or a {@code NativeFunctionPointer} to a
 * native foreign function and a {@code NativeLibraryHandle} of a library. A
 * {@code NativeFunctionPointer} wraps the raw function pointer. A {@code NativeFunctionHandle} is a
 * callable representation of a native target function in Java.
 * <p>
 * To resolve a {@code NativeFunctionHandle}, one has to provide the signature of the native target
 * function according to the calling convention of the target platform (e.g. Unix AMD64:
 * {@link "http://www.uclibc.org/docs/psABI-x86_64.pdf"}). The signature contains the type (e.g.
 * {@code int.class} for a C integer, ( {@code long.class} for a 64bit pointer) of each value, which
 * is passed to the native target function.
 */
public interface NativeFunctionInterface {

    /**
     * Resolves and returns a library handle.
     * 
     * @param libPath the absolute path to the library
     * @return the resolved library handle
     */
    NativeLibraryHandle getLibraryHandle(String libPath);

    /**
     * Resolves the {@code NativeFunctionHandle} of a native function that can be called. Use a
     * {@code NativeFunctionHandle} to invoke the native target function.
     * 
     * @param libraryHandle the handle to a resolved library
     * @param functionName the name of the function to be resolved
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native foreign function
     */
    NativeFunctionHandle getFunctionHandle(NativeLibraryHandle libraryHandle, String functionName, Class returnType, Class[] argumentTypes);

    /**
     * Resolves the {@code NativeFunctionHandle} of a native function that can be called. Use a
     * {@code NativeFunctionHandle} to invoke the native target function.
     * 
     * @param functionPointer the function pointer
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native foreign function
     */
    NativeFunctionHandle getFunctionHandle(NativeFunctionPointer functionPointer, Class returnType, Class[] argumentTypes);

    /**
     * Resolves the function pointer {@code NativeFunctionPointer} of a native function. A
     * {@code NativeFunctionPointer} wraps the raw pointer value.
     * 
     * @param libraryHandles the handles to a various resolved library, the first library containing
     *            the method wins
     * @param functionName the name of the function to be resolved
     * @return the function handle of the native foreign function
     */
    NativeFunctionPointer getFunctionPointer(NativeLibraryHandle[] libraryHandles, String functionName);

    /**
     * Resolves the {@code NativeFunctionHandle} of a native function that can be called. Use a
     * {@code NativeFunctionHandle} to invoke the native target function.
     * 
     * @param libraryHandles the handles to a various resolved library, the first library containing
     *            the method wins
     * @param functionName the name of the function to be resolved
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native foreign function
     */
    NativeFunctionHandle getFunctionHandle(NativeLibraryHandle[] libraryHandles, String functionName, Class returnType, Class[] argumentTypes);

    /**
     * Resolves the {@code NativeFunctionHandle} of a native function that can be called. Use a
     * {@code NativeFunctionHandle} to invoke the native target function.
     * 
     * @param functionName the name of the function to be resolved
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native foreign function
     */
    NativeFunctionHandle getFunctionHandle(String functionName, Class returnType, Class[] argumentTypes);

    /**
     * Creates {@code NativeFunctionPointer} from raw value. A {@code NativeFunctionPointer} wraps
     * the raw pointer value.
     * 
     * @param rawValue Raw pointer value
     * @return {@code NativeFunctionPointer} of the raw pointer
     */
    NativeFunctionPointer getNativeFunctionPointerFromRawValue(long rawValue);
}
