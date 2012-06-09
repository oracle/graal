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

    byte[] RiMethod_code(HotSpotMethodResolved method);

    String RiMethod_signature(HotSpotMethodResolved method);

    ExceptionHandler[] RiMethod_exceptionHandlers(HotSpotMethodResolved method);

    boolean RiMethod_hasBalancedMonitors(HotSpotMethodResolved method);

    JavaMethod RiMethod_uniqueConcreteMethod(HotSpotMethodResolved method);

    int RiMethod_invocationCount(HotSpotMethodResolved method);

    HotSpotMethodData RiMethod_methodData(HotSpotMethodResolved method);

    JavaType RiSignature_lookupType(String returnType, HotSpotTypeResolved accessingClass, boolean eagerResolve);

    Object RiConstantPool_lookupConstant(HotSpotTypeResolved pool, int cpi);

    JavaMethod RiConstantPool_lookupMethod(HotSpotTypeResolved pool, int cpi, byte byteCode);

    JavaType RiConstantPool_lookupType(HotSpotTypeResolved pool, int cpi);

    JavaField RiConstantPool_lookupField(HotSpotTypeResolved pool, int cpi, byte byteCode);

    void RiConstantPool_loadReferencedType(HotSpotTypeResolved pool, int cpi, byte byteCode);

    HotSpotCompiledMethod installMethod(HotSpotTargetMethod targetMethod, boolean makeDefault, HotSpotCodeInfo info);

    HotSpotVMConfig getConfiguration();

    JavaMethod RiType_resolveMethodImpl(HotSpotTypeResolved klass, String name, String signature);

    boolean RiType_isSubtypeOf(HotSpotTypeResolved klass, JavaType other);

    JavaType RiType_leastCommonAncestor(HotSpotTypeResolved thisType, HotSpotTypeResolved otherType);

    JavaType getPrimitiveArrayType(Kind kind);

    JavaType RiType_arrayOf(HotSpotTypeResolved klass);

    JavaType RiType_componentType(HotSpotTypeResolved klass);

    boolean RiType_isInitialized(HotSpotTypeResolved klass);

    JavaType getType(Class<?> javaClass);

    JavaType RiType_uniqueConcreteSubtype(HotSpotTypeResolved klass);

    JavaType RiType_superType(HotSpotTypeResolved klass);

    int getArrayLength(Constant array);

    boolean compareConstantObjects(Constant x, Constant y);

    JavaType getRiType(Constant constant);

    ResolvedJavaField[] RiType_fields(HotSpotTypeResolved klass);

    boolean RiMethod_hasCompiledCode(HotSpotMethodResolved method);

    int RiMethod_getCompiledCodeSize(HotSpotMethodResolved method);

    JavaMethod getRiMethod(Method reflectionMethod);

    long getMaxCallTargetOffset(RuntimeCall rtcall);

    String disassembleNative(byte[] code, long address);

    String disassembleJava(HotSpotMethodResolved method);

    StackTraceElement RiMethod_toStackTraceElement(HotSpotMethodResolved method, int bci);

    Object executeCompiledMethod(HotSpotCompiledMethod method, Object arg1, Object arg2, Object arg3);

    Object executeCompiledMethodVarargs(HotSpotCompiledMethod method, Object... args);

    int RiMethod_vtableEntryOffset(HotSpotMethodResolved method);

    long[] getDeoptedLeafGraphIds();

    String decodePC(long pc);

    // Checkstyle: resume
}
