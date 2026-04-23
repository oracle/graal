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
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.shared.resolver.CallKind;

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

    DynamicHub createHub(ParserKlass parsed, ClassDefinitionInfo info, int typeID, String externalName, Module module, ClassLoader classLoader, Class<?> superClass,
                    Class<?>[] superInterfaces);

    DynamicHub getOrCreateArrayHub(DynamicHub dynamicHub);

    /**
     * Creates a new instance of {@code type} without running any constructor yet. The caller should
     * make sure to run a constructor before publishing the result.
     * <p>
     * This entry point is used by reflection support and therefore reports instantiation failures
     * with reflection-compatible exceptions.
     *
     * @throws InstantiationException if {@code type} cannot be instantiated, matching the exception
     *             expected by reflection callers
     */
    Object allocateInstance(ResolvedJavaType type) throws InstantiationException;

    Object execute(ResolvedJavaMethod targetMethod, Object[] args, CallKind callKind);

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

    Class<?> findLoadedClass(Symbol<Type> type, ClassLoader loader);

    Object getStaticStorage(Class<?> cls, boolean primitives, int layerNum);

    ResolvedJavaMethod findMethodHandleIntrinsic(ResolvedJavaMethod signaturePolymorphicMethod, Symbol<Signature> signature);

    Class<?> computeDeclaringClass(DynamicHub hub);

    Object[] computeEnclosingMethod(DynamicHub hub);

    // region linking

    /**
     * Performs class preparation ({@code JVMS 5.4.2}) and class verification ({@code JVMS 5.4.1})
     * for the given class.
     * <p>
     * Note: This method expects the caller to have already performed synchronization.
     */
    void prepareAndVerify(DynamicHub hub);

    /**
     * Records that the class loader {@code loader} loads the type {@code type} as {@code hub}.
     * <p>
     * This is used for subsequent loading constraints checks.
     */
    void recordLoadingConstraint(Symbol<Type> type, DynamicHub hub, ClassLoader loader);

    /**
     * Checks and ensures that both {@code loader1} and {@code loader2} load {@code type} as the
     * same Class (w.r.t. identity).
     */
    void checkLoadingConstraint(Symbol<Type> type, ClassLoader loader1, ClassLoader loader2);

    /**
     * Frees the memory associated with GC'ed class loaders in the loading constraints.
     */
    void purgeLoadingConstraints();

    // endregion linking

    static CremaSupport singleton() {
        return ImageSingletons.lookup(CremaSupport.class);
    }

    CFunctionPointer getEnterDirectInterpreterStubEntryPoint();

    @Platforms(Platform.HOSTED_ONLY.class)
    void setEnterDirectInterpreterStubEntryPoint(CFunctionPointer stubEntryPoint);

    <T extends ConstantPool & jdk.vm.ci.meta.ConstantPool> T getConstantPool(DynamicHub hub);

    void verifySuperAccesses(String externalName, ClassLoader loader, ByteSequence pkgName, Module module,
                    Class<?> superClass, Class<?>[] superInterfaces);
}
