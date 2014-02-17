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
 * Interface to get a {@linkplain NativeFunctionHandle handle} or {@linkplain NativeFunctionPointer
 * pointer} to a native function or a {@linkplain NativeLibraryHandle handle} to an open native
 * library.
 */
public interface NativeFunctionInterface {

    /**
     * Resolves and returns a handle to an open native library. This method will open the library
     * only if it is not already open.
     * 
     * @param libPath the absolute path to the library
     * @return the resolved library handle
     * @throws UnsatisfiedLinkError if the library could not be found or opened
     */
    NativeLibraryHandle getLibraryHandle(String libPath);

    /**
     * Determines if the underlying platform/runtime supports the notion of a default library search
     * path. For example, on *nix systems, this is typically defined by the {@code LD_LIBRARY_PATH}
     * environment variable.
     */
    boolean isDefaultLibrarySearchSupported();

    /**
     * Resolves the function pointer {@code NativeFunctionPointer} of a native function.
     * 
     * @param libraries the ordered list of libraries to search for the function
     * @param name the name of the function to be resolved
     * @return a pointer to the native function
     * @throws UnsatisfiedLinkError if the function could not be resolved
     */
    NativeFunctionPointer getFunctionPointer(NativeLibraryHandle[] libraries, String name);

    /**
     * Resolves a function name to a {@linkplain NativeFunctionHandle handle} that can be called
     * with a given signature. The signature contains the types of the arguments that will be passed
     * to the handle when it is {@linkplain NativeFunctionHandle#call(Object...) called}.
     * 
     * @param library the handle to a resolved library
     * @param name the name of the function to be resolved
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native function
     * @throws UnsatisfiedLinkError if the function handle could not be resolved
     */
    NativeFunctionHandle getFunctionHandle(NativeLibraryHandle library, String name, Class returnType, Class... argumentTypes);

    /**
     * Resolves a function pointer to a {@linkplain NativeFunctionHandle handle} that can be called
     * with a given signature. The signature contains the types of the arguments that will be passed
     * to the handle when it is {@linkplain NativeFunctionHandle#call(Object...) called}.
     * 
     * @param functionPointer a function pointer
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native function
     * @throws UnsatisfiedLinkError the function handle could not be created
     */
    NativeFunctionHandle getFunctionHandle(NativeFunctionPointer functionPointer, Class returnType, Class... argumentTypes);

    /**
     * Resolves a function name to a {@linkplain NativeFunctionHandle handle} that can be called
     * with a given signature. The signature contains the types of the arguments that will be passed
     * to the handle when it is {@linkplain NativeFunctionHandle#call(Object...) called}.
     * 
     * @param libraries the ordered list of libraries to search for the function
     * @param name the name of the function to be resolved
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native function
     * @throws UnsatisfiedLinkError if the function handle could not be created
     */
    NativeFunctionHandle getFunctionHandle(NativeLibraryHandle[] libraries, String name, Class returnType, Class... argumentTypes);

    /**
     * Resolves a function name to a {@linkplain NativeFunctionHandle handle} that can be called
     * with a given signature. The signature contains the types of the arguments that will be passed
     * to the handle when it is {@linkplain NativeFunctionHandle#call(Object...) called}.
     * 
     * @param name the name of the function to be resolved
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native function
     * @throws UnsatisfiedLinkError if default library searching is not
     *             {@linkplain #isDefaultLibrarySearchSupported() supported} or if the function
     *             could not be resolved
     */
    NativeFunctionHandle getFunctionHandle(String name, Class returnType, Class... argumentTypes);

    /**
     * Creates a {@link NativeFunctionPointer} from a raw value.
     * 
     * @param rawValue raw function pointer
     * @return {@code NativeFunctionPointer} for {@code rawValue}
     */
    NativeFunctionPointer getNativeFunctionPointerFromRawValue(long rawValue);
}
