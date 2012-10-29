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

    byte[] getBytecode(HotSpotResolvedJavaMethod method);

    String getSignature(HotSpotResolvedJavaMethod method);

    ExceptionHandler[] getExceptionHandlers(HotSpotResolvedJavaMethod method);

    boolean hasBalancedMonitors(HotSpotResolvedJavaMethod method);

    JavaMethod getUniqueConcreteMethod(HotSpotResolvedJavaMethod method);

    int getInvocationCount(HotSpotResolvedJavaMethod method);

    HotSpotMethodData getMethodData(HotSpotResolvedJavaMethod method);

    JavaType lookupType(String name, HotSpotResolvedJavaType accessingClass, boolean eagerResolve);

    Object lookupConstantInPool(HotSpotResolvedJavaType pool, int cpi);

    JavaMethod lookupMethodInPool(HotSpotResolvedJavaType pool, int cpi, byte opcode);

    JavaType lookupTypeInPool(HotSpotResolvedJavaType pool, int cpi);

    JavaField lookupFieldInPool(HotSpotResolvedJavaType pool, int cpi, byte opcode);

    void lookupReferencedTypeInPool(HotSpotResolvedJavaType pool, int cpi, byte opcode);

    HotSpotCompiledMethod installMethod(HotSpotCompilationResult compResult, boolean makeDefault, HotSpotCodeInfo info);

    void initializeConfiguration(HotSpotVMConfig config);

    JavaMethod resolveMethod(HotSpotResolvedJavaType klass, String name, String signature);

    boolean isSubtypeOf(HotSpotResolvedJavaType klass, JavaType other);

    JavaType getLeastCommonAncestor(HotSpotResolvedJavaType thisType, HotSpotResolvedJavaType otherType);

    JavaType getPrimitiveArrayType(Kind kind);

    JavaType getArrayOf(HotSpotResolvedJavaType klass);

    JavaType getComponentType(HotSpotResolvedJavaType klass);

    boolean isTypeInitialized(HotSpotResolvedJavaType klass);

    void initializeType(HotSpotResolvedJavaType klass);

    JavaType getType(Class<?> javaClass);

    JavaType getUniqueConcreteSubtype(HotSpotResolvedJavaType klass);

    JavaType getSuperType(HotSpotResolvedJavaType klass);

    int getArrayLength(Constant array);

    boolean compareConstantObjects(Constant x, Constant y);

    JavaType getJavaType(Constant constant);

    ResolvedJavaField[] getFields(HotSpotResolvedJavaType klass);

    int getCompiledCodeSize(HotSpotResolvedJavaMethod method);

    JavaMethod getJavaMethod(Method reflectionMethod);

    ResolvedJavaField getJavaField(Field reflectionField);

    long getMaxCallTargetOffset(long stub);

    String disassembleNative(byte[] code, long address);

    StackTraceElement getStackTraceElement(HotSpotResolvedJavaMethod method, int bci);

    Object executeCompiledMethod(HotSpotCompiledMethod method, Object arg1, Object arg2, Object arg3);

    Object executeCompiledMethodVarargs(HotSpotCompiledMethod method, Object... args);

    int getVtableEntryOffset(HotSpotResolvedJavaMethod method);

    long[] getDeoptedLeafGraphIds();

    String decodePC(long pc);

    long getPrototypeMarkWord(HotSpotResolvedJavaType hotSpotResolvedJavaType);
}
