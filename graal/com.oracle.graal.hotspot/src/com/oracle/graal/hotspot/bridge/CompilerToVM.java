/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.bridge;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * Calls from Java into HotSpot.
 */
public interface CompilerToVM {

    /**
     * Copies the original bytecode of a given method into a given byte array.
     *
     * @param metaspaceMethod the metaspace Method object
     * @param code the array into which to copy the original bytecode
     * @return the value of {@code code}
     */
    byte[] initializeBytecode(long metaspaceMethod, byte[] code);

    String getSignature(long metaspaceMethod);

    ExceptionHandler[] initializeExceptionHandlers(long metaspaceMethod, ExceptionHandler[] handlers);

    /**
     * Determines if a given metaspace Method object has balanced monitors.
     *
     * @param metaspaceMethod the metaspace Method object to query
     * @return true if the method has balanced monitors
     */
    boolean hasBalancedMonitors(long metaspaceMethod);

    /**
     * Determines if a given metaspace Method object is compilable. A method may not be compilable
     * for a number of reasons such as:
     * <ul>
     * <li>a CompileOracle directive may prevent compilation of methods</li>
     * <li>the method may have a bytecode breakpoint set</li>
     * <li>the method may have other bytecode features that require special handling by the VM</li>
     * </ul>
     *
     * A non-compilable method should not be inlined.
     *
     * @param metaspaceMethod the metaspace Method object to query
     * @return true if the method is compilable
     */
    boolean isMethodCompilable(long metaspaceMethod);

    /**
     * Used to implement {@link ResolvedJavaType#findUniqueConcreteMethod(ResolvedJavaMethod)}.
     *
     * @param metaspaceMethod the metaspace Method on which to based the search
     * @param resultHolder the holder of the result is put in element 0 of this array
     * @return the metaspace Method result or 0 is there is no unique concrete method for {@code metaspaceMethod}
     */
    long getUniqueConcreteMethod(long metaspaceMethod, HotSpotResolvedJavaType[] resultHolder);

    /**
     * Gets the invocation count for a method.
     *
     * @param metaspaceMethod the metaspace Method object to query
     * @return the invocation count for the method
     */
    int getInvocationCount(long metaspaceMethod);

    /**
     * Initializes a {@link HotSpotResolvedJavaMethod} object from a metaspace Method object.
     *
     * @param metaspaceMethod the metaspace Method object
     * @param method address of a metaspace Method object
     */
    void initializeMethod(long metaspaceMethod, HotSpotResolvedJavaMethod method);

    /**
     * Initializes a {@link HotSpotMethodData} object from a metaspace MethodData object.
     *
     * @param metaspaceMethodData the metaspace MethodData object
     * @param methodData the object to initialize from the metaspace object
     */
    void initializeMethodData(long metaspaceMethodData, HotSpotMethodData methodData);

    /**
     * Converts a name to a Java type.
     *
     * @param name a well formed Java type in {@linkplain JavaType#getName() internal} format
     * @param accessingClass the context of resolution (may be null)
     * @param eagerResolve force resolution to a {@link ResolvedJavaType}. If true, this method will either return a
     *            {@link ResolvedJavaType} or throw an exception
     * @return a Java type for {@code name} which is guaranteed to be of type {@link ResolvedJavaType} if
     *         {@code eagerResolve == true}
     * @throws LinkageError if {@code eagerResolve == true} and the resolution failed
     */
    JavaType lookupType(String name, HotSpotResolvedJavaType accessingClass, boolean eagerResolve);

    Object lookupConstantInPool(HotSpotResolvedJavaType pool, int cpi);

    JavaMethod lookupMethodInPool(HotSpotResolvedJavaType pool, int cpi, byte opcode);

    JavaType lookupTypeInPool(HotSpotResolvedJavaType pool, int cpi);

    JavaField lookupFieldInPool(HotSpotResolvedJavaType pool, int cpi, byte opcode);

    void lookupReferencedTypeInPool(HotSpotResolvedJavaType pool, int cpi, byte opcode);

    /**
     * Installs the result of a compilation into the code cache.
     *
     * @param compResult the result of a compilation
     * @param code if not null, then the code is installed as the non-default compiled code for the associated method
     *            and the details of the installation are written to this object
     * @param info additional information about the installation are written to this object if it is not null
     * @return the value of {@code code} if installation was successful, null otherwise
     */
    HotSpotInstalledCode installCode(HotSpotCompilationResult compResult, HotSpotInstalledCode code, HotSpotCodeInfo info);

    void initializeConfiguration(HotSpotVMConfig config);

    JavaMethod resolveMethod(HotSpotResolvedJavaType klass, String name, String signature);

    boolean isTypeInitialized(HotSpotResolvedJavaType klass);

    void initializeType(HotSpotResolvedJavaType klass);

    ResolvedJavaType getResolvedType(Class<?> javaClass);

    HotSpotResolvedJavaField[] getInstanceFields(HotSpotResolvedJavaType klass);

    /**
     * Gets the compiled code size for a method.
     *
     * @param metaspaceMethod the metaspace Method object to query
     * @return the compiled code size the method
     */
    int getCompiledCodeSize(long metaspaceMethod);

    /**
     * Gets the metaspace Method object corresponding to a given reflection {@link Method} object.
     *
     * @param reflectionMethod
     * @param resultHolder the holder of the result is put in element 0 of this array
     * @return the metaspace Method result for {@code reflectionMethod}
     */
    long getMetaspaceMethod(Method reflectionMethod, HotSpotResolvedJavaType[] resultHolder);

    HotSpotResolvedJavaField getJavaField(Field reflectionField);

    long getMaxCallTargetOffset(long stub);

    String disassembleNative(byte[] code, long address);

    StackTraceElement getStackTraceElement(long metaspaceMethod, int bci);

    Object executeCompiledMethod(long metaspaceMethod, long nmethod, Object arg1, Object arg2, Object arg3);

    Object executeCompiledMethodVarargs(long metaspaceMethod, long nmethod, Object... args);

    int getVtableEntryOffset(long metaspaceMethod);

    long[] getDeoptedLeafGraphIds();

    String decodePC(long pc);

    long getPrototypeMarkWord(HotSpotResolvedJavaType hotSpotResolvedJavaType);
}
