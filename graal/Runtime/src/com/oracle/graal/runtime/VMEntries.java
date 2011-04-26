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

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Entries into the HotSpot VM from Java code.
 *
 * @author Thomas Wuerthinger, Lukas Stadler
 */
public interface VMEntries {

    // Checkstyle: stop

    byte[] RiMethod_code(long vmId);

    int RiMethod_maxStackSize(long vmId);

    int RiMethod_maxLocals(long vmId);

    RiType RiMethod_holder(long vmId);

    String RiMethod_signature(long vmId);

    int RiMethod_accessFlags(long vmId);

    RiType RiSignature_lookupType(String returnType, HotSpotTypeResolved accessingClass);

    Object RiConstantPool_lookupConstant(long vmId, int cpi);

    RiMethod RiConstantPool_lookupMethod(long vmId, int cpi, byte byteCode);

    RiSignature RiConstantPool_lookupSignature(long vmId, int cpi);

    RiType RiConstantPool_lookupType(long vmId, int cpi);

    RiField RiConstantPool_lookupField(long vmId, int cpi, byte byteCode);

    RiConstantPool RiType_constantPool(HotSpotTypeResolved klass);

    void installMethod(HotSpotTargetMethod targetMethod);

    long installStub(HotSpotTargetMethod targetMethod);

    HotSpotVMConfig getConfiguration();

    RiExceptionHandler[] RiMethod_exceptionHandlers(long vmId);

    RiMethod RiType_resolveMethodImpl(HotSpotTypeResolved klass, String name, String signature);

    boolean RiType_isSubtypeOf(HotSpotTypeResolved klass, RiType other);

    RiType getPrimitiveArrayType(CiKind kind);

    RiType RiType_arrayOf(HotSpotTypeResolved klass);

    RiType RiType_componentType(HotSpotTypeResolved klass);

    RiType getType(Class<?> javaClass);

    boolean RiMethod_hasBalancedMonitors(long vmId);

    RiMethod RiMethod_uniqueConcreteMethod(long vmId);

    void recordBailout(String reason);

    RiType RiType_uniqueConcreteSubtype(HotSpotTypeResolved hotSpotTypeResolved);

    RiType RiType_superType(HotSpotTypeResolved hotSpotTypeResolved);

    int getArrayLength(CiConstant array);

    boolean compareConstantObjects(CiConstant x, CiConstant y);

    RiType getRiType(CiConstant constant);

    // Checkstyle: resume
}
