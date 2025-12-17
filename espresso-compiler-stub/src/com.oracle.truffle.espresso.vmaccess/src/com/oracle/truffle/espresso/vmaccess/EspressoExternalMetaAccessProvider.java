/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vmaccess;

import static com.oracle.truffle.espresso.vmaccess.EspressoExternalConstantReflectionProvider.safeGetClass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaType;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

final class EspressoExternalMetaAccessProvider implements MetaAccessProvider {
    private static final ClassLoader PLATFORM_CLASS_LOADER = ClassLoader.getPlatformClassLoader();
    private static final ClassLoader SYSTEM_CLASS_LOADER = ClassLoader.getSystemClassLoader();
    private final EspressoExternalVMAccess access;

    EspressoExternalMetaAccessProvider(EspressoExternalVMAccess access) {
        this.access = access;
    }

    private static boolean isKnownLoader(ClassLoader loader) {
        return loader == null || loader == PLATFORM_CLASS_LOADER || loader == SYSTEM_CLASS_LOADER;
    }

    @Override
    public EspressoResolvedJavaType lookupJavaType(Class<?> clazz) {
        if (!isKnownLoader(clazz.getClassLoader())) {
            throw new IllegalArgumentException("Cannot lookup types with unknown class loader");
        }
        if (clazz.isArray()) {
            int dims = 0;
            Class<?> elemental = clazz;
            do {
                dims++;
                elemental = elemental.getComponentType();
            } while (elemental.isArray());
            return new EspressoExternalResolvedArrayType(lookupNonArrayType(elemental), dims, access);
        }
        return lookupNonArrayType(clazz);
    }

    private EspressoResolvedJavaType lookupNonArrayType(Class<?> clazz) {
        assert !clazz.isArray() : clazz;
        if (clazz.isPrimitive()) {
            return access.forPrimitiveKind(JavaKind.fromJavaClass(clazz));
        }
        Value value = access.lookupMetaObject(clazz.getName());
        if (value.isNull()) {
            throw new NoClassDefFoundError(clazz.getName());
        }
        return new EspressoExternalResolvedInstanceType(access, value);
    }

    @Override
    public ResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod) {
        EspressoResolvedJavaType declaringType = lookupJavaType(reflectionMethod.getDeclaringClass());
        ResolvedJavaMethod[] methods;
        String name;
        EspressoExternalSignature signature = lookupSignature(reflectionMethod);
        if (reflectionMethod instanceof Constructor) {
            methods = declaringType.getDeclaredConstructors();
            name = "<init>";
        } else {
            assert reflectionMethod instanceof Method : reflectionMethod;
            methods = declaringType.getDeclaredMethods();
            name = reflectionMethod.getName();
        }
        ResolvedJavaMethod result = findMethod(methods, name, signature);
        if (result == null) {
            throw new NoSuchMethodError(reflectionMethod.toString());
        }
        return result;
    }

    private EspressoExternalSignature lookupSignature(Executable reflectionExecutable) {
        StringBuilder sb = new StringBuilder("(");
        for (Parameter p : reflectionExecutable.getParameters()) {
            appendType(sb, p.getType());
        }
        sb.append(')');
        if (reflectionExecutable instanceof Method reflectionMethod) {
            appendType(sb, reflectionMethod.getReturnType());
        } else {
            sb.append('V');
        }
        return new EspressoExternalSignature(access, sb.toString());
    }

    private static void appendType(StringBuilder sb, Class<?> clazz) {
        if (clazz.isArray()) {
            Class<?> t = clazz;
            do {
                sb.append('[');
                t = t.getComponentType();
            } while (t.isArray());
            appendNonArrayType(sb, clazz);
        } else {
            appendNonArrayType(sb, clazz);
        }
    }

    private static void appendNonArrayType(StringBuilder sb, Class<?> clazz) {
        assert !clazz.isArray() : clazz;
        if (clazz.isPrimitive()) {
            sb.append(JavaKind.fromJavaClass(clazz).getTypeChar());
        } else {
            sb.append('L');
            String name = clazz.getName();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == '.') {
                    sb.append('/');
                } else {
                    sb.append(c);
                }
            }
            sb.append(';');
        }
    }

    private static ResolvedJavaMethod findMethod(ResolvedJavaMethod[] methods, String name, Signature signature) {
        for (ResolvedJavaMethod method : methods) {
            if (method.getName().equals(name) && signature.equals(method.getSignature())) {
                return method;
            }
        }
        return null;
    }

    @Override
    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public ResolvedJavaType lookupJavaType(JavaConstant constant) {
        if (constant.isNull() || constant.getJavaKind().isPrimitive()) {
            return null;
        }
        if (!(constant instanceof EspressoExternalObjectConstant objectConstant)) {
            throw new IllegalArgumentException("expected an EspressoExternalObjectConstant got " + safeGetClass(constant));
        }
        return objectConstant.getType();
    }

    @Override
    public Signature parseMethodDescriptor(String methodDescriptor) {
        return new EspressoExternalSignature(access, methodDescriptor);
    }

    @Override
    public long getMemorySize(JavaConstant constant) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int debugId) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public JavaConstant encodeSpeculation(SpeculationLog.Speculation speculation) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public SpeculationLog.Speculation decodeSpeculation(JavaConstant constant, SpeculationLog speculationLog) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public int decodeDebugId(JavaConstant constant) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public int getArrayBaseOffset(JavaKind elementKind) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public int getArrayIndexScale(JavaKind elementKind) {
        throw JVMCIError.unimplemented();
    }
}
