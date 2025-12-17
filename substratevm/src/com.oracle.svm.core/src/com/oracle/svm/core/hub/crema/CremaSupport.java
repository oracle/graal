/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.hub.crema;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

public interface CremaSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    ResolvedJavaType createInterpreterType(DynamicHub hub, ResolvedJavaType analysisType);

    Target_java_lang_invoke_MemberName resolveMemberName(Target_java_lang_invoke_MemberName mn, Class<?> caller);

    Object invokeBasic(Target_java_lang_invoke_MemberName memberName, Object methodHandle, Object[] args);

    Object linkToVirtual(Object[] args);

    Object linkToStatic(Object[] args);

    Object linkToSpecial(Object[] args);

    Object linkToInterface(Object[] args);

    Object getStaticStorage(ResolvedJavaField resolved);

    DynamicHub createHub(ParserKlass parsed, ClassDefinitionInfo info, int typeID, String externalName, Module module, ClassLoader classLoader, Class<?> superClass, Class<?>[] superInterfaces);

    DynamicHub getOrCreateArrayHub(DynamicHub dynamicHub);

    /**
     * Creates a new instance of {@code type} without running any constructor yet. The caller should
     * make sure to run a constructor before publishing the result.
     */
    Object allocateInstance(ResolvedJavaType type);

    Object execute(ResolvedJavaMethod targetMethod, Object[] args, boolean isVirtual);

    Class<?> toClass(ResolvedJavaType resolvedJavaType);

    default Class<?> resolveOrThrow(UnresolvedJavaType unresolvedJavaType, ResolvedJavaType accessingClass) {
        ByteSequence type = ByteSequence.create(unresolvedJavaType.getName());
        Symbol<Type> symbolicType = SymbolsSupport.getTypes().getOrCreateValidType(type);
        return resolveOrThrow(symbolicType, accessingClass);
    }

    Class<?> resolveOrThrow(Symbol<Type> type, ResolvedJavaType accessingClass);

    default Class<?> resolveOrNull(UnresolvedJavaType unresolvedJavaType, ResolvedJavaType accessingClass) {
        ByteSequence type = ByteSequence.create(unresolvedJavaType.getName());
        Symbol<Type> symbolicType = SymbolsSupport.getTypes().getOrCreateValidType(type);
        return resolveOrNull(symbolicType, accessingClass);
    }

    Class<?> resolveOrNull(Symbol<Type> type, ResolvedJavaType accessingClass);

    default Class<?> findLoadedClass(UnresolvedJavaType unresolvedJavaType, ResolvedJavaType accessingClass) {
        ByteSequence type = ByteSequence.create(unresolvedJavaType.getName());
        Symbol<Type> symbolicType = SymbolsSupport.getTypes().getOrCreateValidType(type);
        return findLoadedClass(symbolicType, accessingClass);
    }

    Class<?> findLoadedClass(Symbol<Type> type, ResolvedJavaType accessingClass);

    Object getStaticStorage(Class<?> cls, boolean primitives, int layerNum);

    ResolvedJavaMethod findMethodHandleIntrinsic(ResolvedJavaMethod signaturePolymorphicMethod, Symbol<Signature> signature);

    Object computeEnclosingClass(DynamicHub hub);

    static CremaSupport singleton() {
        return ImageSingletons.lookup(CremaSupport.class);
    }
}
