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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * Entries into the HotSpot VM from Java code.
 */
public class CompilerToVMImpl implements CompilerToVM {

    private native int installCode0(HotSpotCompiledCode compiledCode, HotSpotInstalledCode code, SpeculationLog speculationLog);

    @Override
    public CodeInstallResult installCode(HotSpotCompiledCode compiledCode, HotSpotInstalledCode code, SpeculationLog speculationLog) {
        return CodeInstallResult.getEnum(installCode0(compiledCode, code, speculationLog));
    }

    @Override
    public native long getMetaspaceMethod(Class<?> holder, int slot);

    @Override
    public native byte[] initializeBytecode(long metaspaceMethod, byte[] code);

    @Override
    public native long exceptionTableStart(long metaspaceMethod);

    @Override
    public native boolean hasBalancedMonitors(long metaspaceMethod);

    @Override
    public native long findUniqueConcreteMethod(long metaspaceMethod);

    @Override
    public native long getKlassImplementor(long metaspaceKlass);

    @Override
    public native long lookupType(String name, Class<?> accessingClass, boolean eagerResolve);

    @Override
    public native Object lookupConstantInPool(long metaspaceConstantPool, int cpi);

    @Override
    public native JavaMethod lookupMethodInPool(long metaspaceConstantPool, int cpi, byte opcode);

    @Override
    public native JavaType lookupTypeInPool(long metaspaceConstantPool, int cpi);

    @Override
    public native JavaField lookupFieldInPool(long metaspaceConstantPool, int cpi, byte opcode);

    @Override
    public native void lookupReferencedTypeInPool(long metaspaceConstantPool, int cpi, byte opcode);

    @Override
    public native Object lookupAppendixInPool(long metaspaceConstantPool, int cpi, byte opcode);

    @Override
    public native void initializeConfiguration(HotSpotVMConfig config);

    @Override
    public native long resolveMethod(HotSpotResolvedObjectType klass, String name, String signature);

    @Override
    public native boolean hasFinalizableSubclass(HotSpotResolvedObjectType klass);

    @Override
    public native void initializeMethod(long metaspaceMethod, HotSpotResolvedJavaMethod method);

    @Override
    public native HotSpotResolvedJavaField[] getInstanceFields(HotSpotResolvedObjectType klass);

    @Override
    public native long getClassInitializer(HotSpotResolvedObjectType klass);

    @Override
    public native int getCompiledCodeSize(long metaspaceMethod);

    @Override
    public native long getMaxCallTargetOffset(long address);

    // The HotSpot disassembler seems not to be thread safe so it's better to synchronize its usage
    @Override
    public synchronized native String disassembleCodeBlob(long codeBlob);

    @Override
    public native StackTraceElement getStackTraceElement(long metaspaceMethod, int bci);

    @Override
    public native Object executeCompiledMethodVarargs(Object[] args, HotSpotInstalledCode hotspotInstalledCode);

    @Override
    public native long[] getDeoptedLeafGraphIds();

    @Override
    public native long[] getLineNumberTable(HotSpotResolvedJavaMethod method);

    @Override
    public native long getLocalVariableTableStart(HotSpotResolvedJavaMethod method);

    @Override
    public native int getLocalVariableTableLength(HotSpotResolvedJavaMethod method);

    @Override
    public native String getFileName(HotSpotResolvedJavaType method);

    @Override
    public native void reprofile(long metaspaceMethod);

    @Override
    public native void invalidateInstalledCode(HotSpotInstalledCode hotspotInstalledCode);

    @Override
    public native Object readUnsafeUncompressedPointer(Object o, long displacement);

    @Override
    public native long readUnsafeKlassPointer(Object o);

    @Override
    public native void doNotInlineOrCompile(long metaspaceMethod);

    @Override
    public Object executeCompiledMethod(Object arg1, Object arg2, Object arg3, HotSpotInstalledCode hotspotInstalledCode) throws InvalidInstalledCodeException {
        return executeCompiledMethodIntrinsic(arg1, arg2, arg3, hotspotInstalledCode);
    }

    public synchronized native void notifyCompilationStatistics(int id, HotSpotResolvedJavaMethod method, boolean osr, int processedBytecodes, long time, long timeUnitsPerSecond,
                    HotSpotInstalledCode installedCode);

    public synchronized native void printCompilationStatistics(boolean perCompiler, boolean aggregate);

    public native void resetCompilationStatistics();

    /**
     * Direct call to the given nmethod with three object arguments and an object return value. This
     * method does not have an implementation on the C++ side, but its entry points (from
     * interpreter and from compiled code) are directly pointing to a manually generated assembly
     * stub that does the necessary argument shuffling and a tail call via an indirect jump to the
     * verified entry point of the given native method.
     */
    public static native Object executeCompiledMethodIntrinsic(Object arg1, Object arg2, Object arg3, HotSpotInstalledCode hotspotInstalledCode) throws InvalidInstalledCodeException;

    public native long[] collectCounters();

    public native boolean isMature(long method);

    public native int allocateCompileId(HotSpotResolvedJavaMethod method, int entryBCI);

    public native String getGPUs();

    public native boolean canInlineMethod(long metaspaceMethod);

    public native boolean shouldInlineMethod(long metaspaceMethod);
}
