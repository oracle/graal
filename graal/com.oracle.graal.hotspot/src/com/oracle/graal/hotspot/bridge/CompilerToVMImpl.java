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
import com.oracle.graal.hotspot.server.*;

/**
 * Entries into the HotSpot VM from Java code.
 */
public class CompilerToVMImpl implements CompilerToVM, Remote {

    // Checkstyle: stop

    @Override
    public native RiMethod getRiMethod(Method reflectionMethod);

    @Override
    public native byte[] RiMethod_code(HotSpotMethodResolved method);

    @Override
    public native String RiMethod_signature(HotSpotMethodResolved method);

    @Override
    public native RiExceptionHandler[] RiMethod_exceptionHandlers(HotSpotMethodResolved method);

    @Override
    public native boolean RiMethod_hasBalancedMonitors(HotSpotMethodResolved method);

    @Override
    public native RiMethod RiMethod_uniqueConcreteMethod(HotSpotMethodResolved method);

    @Override
    public native int RiMethod_invocationCount(HotSpotMethodResolved method);

    @Override
    public native RiType RiSignature_lookupType(String returnType, HotSpotTypeResolved accessingClass, boolean eagerResolve);

    @Override
    public native Object RiConstantPool_lookupConstant(HotSpotTypeResolved pool, int cpi);

    @Override
    public native RiMethod RiConstantPool_lookupMethod(HotSpotTypeResolved pool, int cpi, byte byteCode);

    @Override
    public native RiType RiConstantPool_lookupType(HotSpotTypeResolved pool, int cpi);

    @Override
    public native void RiConstantPool_loadReferencedType(HotSpotTypeResolved pool, int cpi, byte byteCode);

    @Override
    public native RiField RiConstantPool_lookupField(HotSpotTypeResolved pool, int cpi, byte byteCode);

    @Override
    public native HotSpotCompiledMethod installMethod(HotSpotTargetMethod targetMethod, boolean installCode, HotSpotCodeInfo info);

    @Override
    public native long installStub(HotSpotTargetMethod targetMethod, HotSpotCodeInfo info);

    @Override
    public native HotSpotVMConfig getConfiguration();

    @Override
    public native RiMethod RiType_resolveMethodImpl(HotSpotTypeResolved klass, String name, String signature);

    @Override
    public native boolean RiType_isSubtypeOf(HotSpotTypeResolved klass, RiType other);

    @Override
    public native RiType RiType_leastCommonAncestor(HotSpotTypeResolved thisType, HotSpotTypeResolved otherType);

    @Override
    public native RiType getPrimitiveArrayType(CiKind kind);

    @Override
    public native RiType RiType_arrayOf(HotSpotTypeResolved klass);

    @Override
    public native RiType RiType_componentType(HotSpotTypeResolved klass);

    @Override
    public native RiType RiType_uniqueConcreteSubtype(HotSpotTypeResolved klass);

    @Override
    public native RiType RiType_superType(HotSpotTypeResolved klass);

    @Override
    public native boolean RiType_isInitialized(HotSpotTypeResolved klass);

    @Override
    public native HotSpotMethodData RiMethod_methodData(HotSpotMethodResolved method);

    @Override
    public native RiType getType(Class<?> javaClass);

    @Override
    public int getArrayLength(CiConstant array) {
        return Array.getLength(array.asObject());
    }

    @Override
    public boolean compareConstantObjects(CiConstant x, CiConstant y) {
        return x.asObject() == y.asObject();
    }

    @Override
    public RiType getRiType(CiConstant constant) {
        Object o = constant.asObject();
        if (o == null) {
            return null;
        }
        return getType(o.getClass());
    }

    @Override
    public native RiResolvedField[] RiType_fields(HotSpotTypeResolved klass);

    @Override
    public native boolean RiMethod_hasCompiledCode(HotSpotMethodResolved method);

    @Override
    public native int RiMethod_getCompiledCodeSize(HotSpotMethodResolved method);

    @Override
    public native long getMaxCallTargetOffset(CiRuntimeCall rtcall);

    @Override
    public native String disassembleNative(byte[] code, long address);

    @Override
    public native String disassembleJava(HotSpotMethodResolved method);

    @Override
    public native StackTraceElement RiMethod_toStackTraceElement(HotSpotMethodResolved method, int bci);

    @Override
    public native Object executeCompiledMethod(HotSpotCompiledMethod method, Object arg1, Object arg2, Object arg3);

    @Override
    public native Object executeCompiledMethodVarargs(HotSpotCompiledMethod method, Object... args);

    @Override
    public native int RiMethod_vtableEntryOffset(HotSpotMethodResolved method);

    @Override
    public native long[] getDeoptedLeafGraphIds();

    // Checkstyle: resume
}
