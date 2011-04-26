/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */

package com.oracle.graal.runtime;

import java.lang.reflect.*;

import com.oracle.graal.runtime.server.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Entries into the HotSpot VM from Java code.
 *
 * @author Thomas Wuerthinger, Lukas Stadler
 */
public class VMEntriesNative implements VMEntries, Remote {

    // Checkstyle: stop
    @Override
    public native byte[] RiMethod_code(long vmId);

    @Override
    public native int RiMethod_maxStackSize(long vmId);

    @Override
    public native int RiMethod_maxLocals(long vmId);

    @Override
    public native RiType RiMethod_holder(long vmId);

    @Override
    public native String RiMethod_signature(long vmId);

    @Override
    public native int RiMethod_accessFlags(long vmId);

    @Override
    public native RiType RiSignature_lookupType(String returnType, HotSpotTypeResolved accessingClass);

    @Override
    public native Object RiConstantPool_lookupConstant(long vmId, int cpi);

    @Override
    public native RiMethod RiConstantPool_lookupMethod(long vmId, int cpi, byte byteCode);

    @Override
    public native RiSignature RiConstantPool_lookupSignature(long vmId, int cpi);

    @Override
    public native RiType RiConstantPool_lookupType(long vmId, int cpi);

    @Override
    public native RiField RiConstantPool_lookupField(long vmId, int cpi, byte byteCode);

    @Override
    public native RiConstantPool RiType_constantPool(HotSpotTypeResolved klass);

    @Override
    public native void installMethod(HotSpotTargetMethod targetMethod);

    @Override
    public native long installStub(HotSpotTargetMethod targetMethod);

    @Override
    public native HotSpotVMConfig getConfiguration();

    @Override
    public native RiExceptionHandler[] RiMethod_exceptionHandlers(long vmId);

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
    public native RiType getType(Class<?> javaClass);

    @Override
    public native boolean RiMethod_hasBalancedMonitors(long vmId);

    @Override
    public native void recordBailout(String reason);

    @Override
    public native RiMethod RiMethod_uniqueConcreteMethod(long vmId);

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

    // Checkstyle: resume
}
