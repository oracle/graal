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

package com.oracle.max.graal.hotspot;

import java.lang.reflect.*;

import com.oracle.max.graal.hotspot.server.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Entries into the HotSpot VM from Java code.
 */
public class VMEntriesNative implements VMEntries, Remote {

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
    public native int RiMethod_exceptionProbability(HotSpotMethodResolved method, int bci);

    @Override
    public native RiTypeProfile RiMethod_typeProfile(HotSpotMethodResolved method, int bci);

    @Override
    public native double RiMethod_branchProbability(HotSpotMethodResolved method, int bci);

    @Override
    public native double[] RiMethod_switchProbability(HotSpotMethodResolved method, int bci);

    @Override
    public native RiType RiSignature_lookupType(String returnType, HotSpotTypeResolved accessingClass);

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
    public native HotSpotCompiledMethod installMethod(HotSpotTargetMethod targetMethod, boolean installCode);

    @Override
    public native long installStub(HotSpotTargetMethod targetMethod);

    @Override
    public native HotSpotVMConfig getConfiguration();

    @Override
    public native RiMethod RiType_resolveMethodImpl(HotSpotTypeResolved klass, String name, String signature);

    @Override
    public native boolean RiType_isSubtypeOf(HotSpotTypeResolved klass, RiType other);

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
    public native long getMaxCallTargetOffset(CiRuntimeCall rtcall);

    @Override
    public native void notifyJavaQueue();

    // Checkstyle: resume
}
