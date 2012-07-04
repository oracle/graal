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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * Calls from Java into HotSpot.
 */
public interface CompilerToVM {

    // Checkstyle: stop

    byte[] JavaMethod_code(HotSpotResolvedJavaMethod method);

    String JavaMethod_signature(HotSpotResolvedJavaMethod method);

    ExceptionHandler[] JavaMethod_exceptionHandlers(HotSpotResolvedJavaMethod method);

    boolean JavaMethod_hasBalancedMonitors(HotSpotResolvedJavaMethod method);

    JavaMethod JavaMethod_uniqueConcreteMethod(HotSpotResolvedJavaMethod method);

    int JavaMethod_invocationCount(HotSpotResolvedJavaMethod method);

    HotSpotMethodData JavaMethod_methodData(HotSpotResolvedJavaMethod method);

    JavaType Signature_lookupType(String returnType, HotSpotResolvedJavaType accessingClass, boolean eagerResolve);

    Object ConstantPool_lookupConstant(HotSpotResolvedJavaType pool, int cpi);

    JavaMethod ConstantPool_lookupMethod(HotSpotResolvedJavaType pool, int cpi, byte byteCode);

    JavaType ConstantPool_lookupType(HotSpotResolvedJavaType pool, int cpi);

    JavaField ConstantPool_lookupField(HotSpotResolvedJavaType pool, int cpi, byte byteCode);

    void ConstantPool_loadReferencedType(HotSpotResolvedJavaType pool, int cpi, byte byteCode);

    HotSpotCompiledMethod installMethod(HotSpotTargetMethod targetMethod, boolean makeDefault, HotSpotCodeInfo info);

    HotSpotVMConfig getConfiguration();

    JavaMethod JavaType_resolveMethodImpl(HotSpotResolvedJavaType klass, String name, String signature);

    boolean JavaType_isSubtypeOf(HotSpotResolvedJavaType klass, JavaType other);

    JavaType JavaType_leastCommonAncestor(HotSpotResolvedJavaType thisType, HotSpotResolvedJavaType otherType);

    JavaType getPrimitiveArrayType(Kind kind);

    JavaType JavaType_arrayOf(HotSpotResolvedJavaType klass);

    JavaType JavaType_componentType(HotSpotResolvedJavaType klass);

    boolean JavaType_isInitialized(HotSpotResolvedJavaType klass);

    JavaType getType(Class<?> javaClass);

    JavaType JavaType_uniqueConcreteSubtype(HotSpotResolvedJavaType klass);

    JavaType JavaType_superType(HotSpotResolvedJavaType klass);

    int getArrayLength(Constant array);

    boolean compareConstantObjects(Constant x, Constant y);

    JavaType getJavaType(Constant constant);

    ResolvedJavaField[] JavaType_fields(HotSpotResolvedJavaType klass);

    boolean JavaMethod_hasCompiledCode(HotSpotResolvedJavaMethod method);

    int JavaMethod_getCompiledCodeSize(HotSpotResolvedJavaMethod method);

    JavaMethod getJavaMethod(Method reflectionMethod);

    long getMaxCallTargetOffset(RuntimeCall rtcall);

    String disassembleNative(byte[] code, long address);

    StackTraceElement JavaMethod_toStackTraceElement(HotSpotResolvedJavaMethod method, int bci);

    Object executeCompiledMethod(HotSpotCompiledMethod method, Object arg1, Object arg2, Object arg3);

    Object executeCompiledMethodVarargs(HotSpotCompiledMethod method, Object... args);

    int JavaMethod_vtableEntryOffset(HotSpotResolvedJavaMethod method);

    long[] getDeoptedLeafGraphIds();

    String decodePC(long pc);

    // Checkstyle: resume
}
