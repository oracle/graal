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

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.ri.*;

/**
 * Calls from Java into HotSpot.
 */
public interface CompilerToVM {

    // Checkstyle: stop

    byte[] RiMethod_code(HotSpotMethodResolved method);

    String RiMethod_signature(HotSpotMethodResolved method);

    RiExceptionHandler[] RiMethod_exceptionHandlers(HotSpotMethodResolved method);

    boolean RiMethod_hasBalancedMonitors(HotSpotMethodResolved method);

    RiMethod RiMethod_uniqueConcreteMethod(HotSpotMethodResolved method);

    int RiMethod_invocationCount(HotSpotMethodResolved method);

    HotSpotMethodData RiMethod_methodData(HotSpotMethodResolved method);

    RiType RiSignature_lookupType(String returnType, HotSpotTypeResolved accessingClass, boolean eagerResolve);

    Object RiConstantPool_lookupConstant(HotSpotTypeResolved pool, int cpi);

    RiMethod RiConstantPool_lookupMethod(HotSpotTypeResolved pool, int cpi, byte byteCode);

    RiType RiConstantPool_lookupType(HotSpotTypeResolved pool, int cpi);

    RiField RiConstantPool_lookupField(HotSpotTypeResolved pool, int cpi, byte byteCode);

    void RiConstantPool_loadReferencedType(HotSpotTypeResolved pool, int cpi, byte byteCode);

    HotSpotCompiledMethod installMethod(HotSpotTargetMethod targetMethod, boolean makeDefault, HotSpotCodeInfo info);

    HotSpotVMConfig getConfiguration();

    RiMethod RiType_resolveMethodImpl(HotSpotTypeResolved klass, String name, String signature);

    boolean RiType_isSubtypeOf(HotSpotTypeResolved klass, RiType other);

    RiType RiType_leastCommonAncestor(HotSpotTypeResolved thisType, HotSpotTypeResolved otherType);

    RiType getPrimitiveArrayType(CiKind kind);

    RiType RiType_arrayOf(HotSpotTypeResolved klass);

    RiType RiType_componentType(HotSpotTypeResolved klass);

    boolean RiType_isInitialized(HotSpotTypeResolved klass);

    RiType getType(Class<?> javaClass);

    RiType RiType_uniqueConcreteSubtype(HotSpotTypeResolved klass);

    RiType RiType_superType(HotSpotTypeResolved klass);

    int getArrayLength(CiConstant array);

    boolean compareConstantObjects(CiConstant x, CiConstant y);

    RiType getRiType(CiConstant constant);

    RiResolvedField[] RiType_fields(HotSpotTypeResolved klass);

    boolean RiMethod_hasCompiledCode(HotSpotMethodResolved method);

    int RiMethod_getCompiledCodeSize(HotSpotMethodResolved method);

    RiMethod getRiMethod(Method reflectionMethod);

    long getMaxCallTargetOffset(CiRuntimeCall rtcall);

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
