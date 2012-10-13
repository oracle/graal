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
 * Entries into the HotSpot VM from Java code.
 */
public class CompilerToVMImpl implements CompilerToVM {

    // Checkstyle: stop

    @Override
    public native JavaMethod getJavaMethod(Method reflectionMethod);

    @Override
    public native ResolvedJavaField getJavaField(Field reflectionMethod);

    @Override
    public native byte[] JavaMethod_code(HotSpotResolvedJavaMethod method);

    @Override
    public native String JavaMethod_signature(HotSpotResolvedJavaMethod method);

    @Override
    public native ExceptionHandler[] JavaMethod_exceptionHandlers(HotSpotResolvedJavaMethod method);

    @Override
    public native boolean JavaMethod_hasBalancedMonitors(HotSpotResolvedJavaMethod method);

    @Override
    public native JavaMethod JavaMethod_uniqueConcreteMethod(HotSpotResolvedJavaMethod method);

    @Override
    public native int JavaMethod_invocationCount(HotSpotResolvedJavaMethod method);

    @Override
    public native JavaType Signature_lookupType(String returnType, HotSpotResolvedJavaType accessingClass, boolean eagerResolve);

    @Override
    public native Object ConstantPool_lookupConstant(HotSpotResolvedJavaType pool, int cpi);

    @Override
    public native JavaMethod ConstantPool_lookupMethod(HotSpotResolvedJavaType pool, int cpi, byte byteCode);

    @Override
    public native JavaType ConstantPool_lookupType(HotSpotResolvedJavaType pool, int cpi);

    @Override
    public native void ConstantPool_loadReferencedType(HotSpotResolvedJavaType pool, int cpi, byte byteCode);

    @Override
    public native JavaField ConstantPool_lookupField(HotSpotResolvedJavaType pool, int cpi, byte byteCode);

    @Override
    public native HotSpotCompiledMethod installMethod(HotSpotCompilationResult comp, boolean makeDefault, HotSpotCodeInfo info);

    @Override
    public native void initializeConfiguration(HotSpotVMConfig config);

    @Override
    public native JavaMethod JavaType_resolveMethodImpl(HotSpotResolvedJavaType klass, String name, String signature);

    @Override
    public native boolean JavaType_isSubtypeOf(HotSpotResolvedJavaType klass, JavaType other);

    @Override
    public native JavaType JavaType_leastCommonAncestor(HotSpotResolvedJavaType thisType, HotSpotResolvedJavaType otherType);

    @Override
    public native JavaType getPrimitiveArrayType(Kind kind);

    @Override
    public native JavaType JavaType_arrayOf(HotSpotResolvedJavaType klass);

    @Override
    public native JavaType JavaType_componentType(HotSpotResolvedJavaType klass);

    @Override
    public native JavaType JavaType_uniqueConcreteSubtype(HotSpotResolvedJavaType klass);

    @Override
    public native JavaType JavaType_superType(HotSpotResolvedJavaType klass);

    @Override
    public native boolean JavaType_isInitialized(HotSpotResolvedJavaType klass);

    @Override
    public native void JavaType_initialize(HotSpotResolvedJavaType klass);

    @Override
    public native HotSpotMethodData JavaMethod_methodData(HotSpotResolvedJavaMethod method);

    @Override
    public native JavaType getType(Class<?> javaClass);

    @Override
    public int getArrayLength(Constant array) {
        return Array.getLength(array.asObject());
    }

    @Override
    public boolean compareConstantObjects(Constant x, Constant y) {
        return x.asObject() == y.asObject();
    }

    @Override
    public JavaType getJavaType(Constant constant) {
        Object o = constant.asObject();
        if (o == null) {
            return null;
        }
        return getType(o.getClass());
    }

    @Override
    public native ResolvedJavaField[] JavaType_fields(HotSpotResolvedJavaType klass);

    @Override
    public native boolean JavaMethod_hasCompiledCode(HotSpotResolvedJavaMethod method);

    @Override
    public native int JavaMethod_getCompiledCodeSize(HotSpotResolvedJavaMethod method);

    @Override
    public native long getMaxCallTargetOffset(long stub);

    @Override
    public native String disassembleNative(byte[] code, long address);

    @Override
    public native StackTraceElement JavaMethod_toStackTraceElement(HotSpotResolvedJavaMethod method, int bci);

    @Override
    public native Object executeCompiledMethod(HotSpotCompiledMethod method, Object arg1, Object arg2, Object arg3);

    @Override
    public native Object executeCompiledMethodVarargs(HotSpotCompiledMethod method, Object... args);

    @Override
    public native int JavaMethod_vtableEntryOffset(HotSpotResolvedJavaMethod method);

    @Override
    public native long[] getDeoptedLeafGraphIds();

    @Override
    public native String decodePC(long pc);

    @Override
    public native long JavaType_prototypeMarkWord(HotSpotResolvedJavaType type);

    // Checkstyle: resume
}
