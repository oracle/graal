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
 * Entries into the HotSpot VM from Java code.
 */
public class CompilerToVMImpl implements CompilerToVM {

    private native int installCode0(HotSpotCompiledCode compiledCode, HotSpotInstalledCode code, boolean[] triggeredDeoptimizations);

    @Override
    public CodeInstallResult installCode(HotSpotCompiledCode compiledCode, HotSpotInstalledCode code, SpeculationLog speculationLog) {
        return CodeInstallResult.values()[installCode0(compiledCode, code, (speculationLog == null) ? null : speculationLog.getRawMap())];
    }

    @Override
    public native long getMetaspaceMethod(Method reflectionMethod, HotSpotResolvedObjectType[] resultHolder);

    @Override
    public native long getMetaspaceConstructor(Constructor reflectionConstructor, HotSpotResolvedObjectType[] resultHolder);

    @Override
    public native HotSpotResolvedJavaField getJavaField(Field reflectionMethod);

    @Override
    public native byte[] initializeBytecode(long metaspaceMethod, byte[] code);

    @Override
    public native String getSignature(long metaspaceMethod);

    @Override
    public native ExceptionHandler[] initializeExceptionHandlers(long metaspaceMethod, ExceptionHandler[] handlers);

    @Override
    public native boolean hasBalancedMonitors(long metaspaceMethod);

    @Override
    public native boolean isMethodCompilable(long metaspaceMethod);

    @Override
    public native long getUniqueConcreteMethod(long metaspaceMethod, HotSpotResolvedObjectType[] resultHolder);

    @Override
    public native ResolvedJavaType getUniqueImplementor(HotSpotResolvedObjectType interfaceType);

    @Override
    public native JavaType lookupType(String name, HotSpotResolvedObjectType accessingClass, boolean eagerResolve);

    @Override
    public native int constantPoolLength(HotSpotResolvedObjectType pool);

    @Override
    public native Object lookupConstantInPool(HotSpotResolvedObjectType pool, int cpi);

    @Override
    public native JavaMethod lookupMethodInPool(HotSpotResolvedObjectType pool, int cpi, byte opcode);

    @Override
    public native JavaType lookupTypeInPool(HotSpotResolvedObjectType pool, int cpi);

    @Override
    public native void lookupReferencedTypeInPool(HotSpotResolvedObjectType pool, int cpi, byte opcode);

    @Override
    public native JavaField lookupFieldInPool(HotSpotResolvedObjectType pool, int cpi, byte opcode);

    @Override
    public native void initializeConfiguration(HotSpotVMConfig config);

    @Override
    public native JavaMethod resolveMethod(HotSpotResolvedObjectType klass, String name, String signature);

    @Override
    public native boolean isTypeInitialized(HotSpotResolvedObjectType klass);

    public native boolean isTypeLinked(HotSpotResolvedObjectType hotSpotResolvedObjectType);

    @Override
    public native boolean hasFinalizableSubclass(HotSpotResolvedObjectType klass);

    @Override
    public native void initializeType(HotSpotResolvedObjectType klass);

    @Override
    public native void initializeMethod(long metaspaceMethod, HotSpotResolvedJavaMethod method);

    @Override
    public native void initializeMethodData(long metaspaceMethodData, HotSpotMethodData methodData);

    @Override
    public native ResolvedJavaType getResolvedType(Class<?> javaClass);

    @Override
    public native HotSpotResolvedJavaField[] getInstanceFields(HotSpotResolvedObjectType klass);

    @Override
    public native HotSpotResolvedJavaMethod[] getMethods(HotSpotResolvedObjectType klass);

    @Override
    public native int getCompiledCodeSize(long metaspaceMethod);

    @Override
    public native long getMaxCallTargetOffset(long address);

    // The HotSpot disassembler seems not to be thread safe so it's better to synchronize its usage
    @Override
    public synchronized native String disassembleCodeBlob(long codeBlob);

    @Override
    public native byte[] getCode(long codeBlob);

    @Override
    public native StackTraceElement getStackTraceElement(long metaspaceMethod, int bci);

    @Override
    public native Object executeCompiledMethodVarargs(Object[] args, HotSpotInstalledCode hotspotInstalledCode);

    @Override
    public native int getVtableEntryOffset(long metaspaceMethod);

    @Override
    public native boolean hasVtableEntry(long metaspaceMethod);

    @Override
    public native long[] getDeoptedLeafGraphIds();

    @Override
    public native long[] getLineNumberTable(HotSpotResolvedJavaMethod method);

    @Override
    public native Local[] getLocalVariableTable(HotSpotResolvedJavaMethod method);

    @Override
    public native String getFileName(HotSpotResolvedJavaType method);

    @Override
    public native void reprofile(long metaspaceMethod);

    @Override
    public native Object lookupAppendixInPool(HotSpotResolvedObjectType pool, int cpi, byte opcode);

    @Override
    public native void invalidateInstalledCode(HotSpotInstalledCode hotspotInstalledCode);

    @Override
    public native Object readUnsafeUncompressedPointer(Object o, long displacement);

    @Override
    public native long readUnsafeKlassPointer(Object o);

    @Override
    public Object executeCompiledMethod(Object arg1, Object arg2, Object arg3, HotSpotInstalledCode hotspotInstalledCode) throws InvalidInstalledCodeException {
        return executeCompiledMethodIntrinsic(arg1, arg2, arg3, hotspotInstalledCode);
    }

    /**
     * Direct call to the given nmethod with three object arguments and an object return value. This
     * method does not have an implementation on the C++ side, but its entry points (from
     * interpreter and from compiled code) are directly pointing to a manually generated assembly
     * stub that does the necessary argument shuffling and a tail call via an indirect jump to the
     * verified entry point of the given native method.
     */
    public static native Object executeCompiledMethodIntrinsic(Object arg1, Object arg2, Object arg3, HotSpotInstalledCode hotspotInstalledCode) throws InvalidInstalledCodeException;
}
