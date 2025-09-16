/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.proxy;

import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.equalsInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.equalsMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.hashCodeInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.hashCodeMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.toStringInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.toStringMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.unproxifyInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.unproxifyMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.CompilationProxyAnnotatedBase.getAnnotationInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.CompilationProxyAnnotatedBase.getAnnotationMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.CompilationProxyAnnotatedBase.getAnnotationsInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.CompilationProxyAnnotatedBase.getAnnotationsMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.CompilationProxyAnnotatedBase.getDeclaredAnnotationsInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.CompilationProxyAnnotatedBase.getDeclaredAnnotationsMethod;

import java.lang.annotation.Annotation;
import java.util.List;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaType;

//JaCoCo Exclude

public sealed class HotSpotResolvedJavaTypeProxy extends HotSpotResolvedJavaType implements CompilationProxy permits HotSpotResolvedObjectTypeProxy {
    private final InvocationHandler handler;

    protected HotSpotResolvedJavaTypeProxy(InvocationHandler handler) {
        super("HotSpotResolvedJavaTypeProxy");
        this.handler = handler;
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotResolvedJavaType.class, name, params);
    }

    protected final Object handle(SymbolicMethod method, InvokableMethod invokable, Object... args) {
        return CompilationProxy.handle(handler, this, method, invokable, args);
    }

    @Override
    protected final HotSpotResolvedObjectType getArrayType() {
        throw new UnsupportedOperationException("getArrayType");
    }

    private static final SymbolicMethod getArrayClassMethod = method("getArrayClass");
    private static final InvokableMethod getArrayClassInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getArrayClass();

    @Override
    public final HotSpotResolvedObjectType getArrayClass() {
        return (HotSpotResolvedObjectType) handle(getArrayClassMethod, getArrayClassInvokable);
    }

    @Override
    protected final boolean isBeingInitialized() {
        throw new UnsupportedOperationException("isBeingInitialized");
    }

    private static final SymbolicMethod resolveMethod = method("resolve", ResolvedJavaType.class);
    private static final InvokableMethod resolveInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).resolve((ResolvedJavaType) args[0]);

    @Override
    public final ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return (ResolvedJavaType) handle(resolveMethod, resolveInvokable, accessingClass);
    }

    private static final SymbolicMethod resolveMethodMethod = method("resolveMethod", ResolvedJavaMethod.class, ResolvedJavaType.class);
    private static final InvokableMethod resolveMethodInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).resolveMethod((ResolvedJavaMethod) args[0], (ResolvedJavaType) args[1]);

    @Override
    public final ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return (ResolvedJavaMethod) handle(resolveMethodMethod, resolveMethodInvokable, method, callerType);
    }

    public static final SymbolicMethod getNameMethod = method("getName");
    public static final InvokableMethod getNameInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getName();

    @Override
    public final String getName() {
        return (String) handle(getNameMethod, getNameInvokable);
    }

    public static final SymbolicMethod getComponentTypeMethod = method("getComponentType");
    public static final InvokableMethod getComponentTypeInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getComponentType();

    @Override
    public final ResolvedJavaType getComponentType() {
        return (ResolvedJavaType) handle(getComponentTypeMethod, getComponentTypeInvokable);
    }

    private static final SymbolicMethod findLeafConcreteSubtypeMethod = method("findLeafConcreteSubtype");
    private static final InvokableMethod findLeafConcreteSubtypeInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).findLeafConcreteSubtype();

    @Override
    @SuppressWarnings("unchecked")
    public final Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        return (Assumptions.AssumptionResult<ResolvedJavaType>) handle(findLeafConcreteSubtypeMethod, findLeafConcreteSubtypeInvokable);
    }

    private static final SymbolicMethod hasFinalizerMethod = method("hasFinalizer");
    private static final InvokableMethod hasFinalizerInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).hasFinalizer();

    @Override
    public final boolean hasFinalizer() {
        return (boolean) handle(hasFinalizerMethod, hasFinalizerInvokable);
    }

    private static final SymbolicMethod hasFinalizableSubclassMethod = method("hasFinalizableSubclass");
    private static final InvokableMethod hasFinalizableSubclassInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).hasFinalizableSubclass();

    @Override
    @SuppressWarnings("unchecked")
    public final Assumptions.AssumptionResult<Boolean> hasFinalizableSubclass() {
        return (Assumptions.AssumptionResult<Boolean>) handle(hasFinalizableSubclassMethod, hasFinalizableSubclassInvokable);
    }

    public static final SymbolicMethod getModifiersMethod = method("getModifiers");
    public static final InvokableMethod getModifiersInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getModifiers();

    @Override
    public final int getModifiers() {
        return (int) handle(getModifiersMethod, getModifiersInvokable);
    }

    public static final SymbolicMethod isInterfaceMethod = method("isInterface");
    public static final InvokableMethod isInterfaceInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isInterface();

    @Override
    public final boolean isInterface() {
        return (boolean) handle(isInterfaceMethod, isInterfaceInvokable);
    }

    private static final SymbolicMethod isInstanceClassMethod = method("isInstanceClass");
    private static final InvokableMethod isInstanceClassInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isInstanceClass();

    @Override
    public final boolean isInstanceClass() {
        return (boolean) handle(isInstanceClassMethod, isInstanceClassInvokable);
    }

    private static final SymbolicMethod isEnumMethod = method("isEnum");
    private static final InvokableMethod isEnumInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isEnum();

    @Override
    public final boolean isEnum() {
        return (boolean) handle(isEnumMethod, isEnumInvokable);
    }

    public static final SymbolicMethod isInitializedMethod = method("isInitialized");
    private static final InvokableMethod isInitializedInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isInitialized();

    @Override
    public final boolean isInitialized() {
        return (boolean) handle(isInitializedMethod, isInitializedInvokable);
    }

    private static final SymbolicMethod initializeMethod = method("initialize");
    private static final InvokableMethod initializeInvokable = (receiver, args) -> {
        ((HotSpotResolvedJavaType) receiver).initialize();
        return null;
    };

    @Override
    public final void initialize() {
        handle(initializeMethod, initializeInvokable);
    }

    private static final SymbolicMethod isLinkedMethod = method("isLinked");
    private static final InvokableMethod isLinkedInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isLinked();

    @Override
    public final boolean isLinked() {
        return (boolean) handle(isLinkedMethod, isLinkedInvokable);
    }

    private static final SymbolicMethod linkMethod = method("link");
    private static final InvokableMethod linkInvokable = (receiver, args) -> {
        ((HotSpotResolvedJavaType) receiver).link();
        return null;
    };

    @Override
    public final void link() {
        handle(linkMethod, linkInvokable);
    }

    private static final SymbolicMethod hasDefaultMethodsMethod = method("hasDefaultMethods");
    private static final InvokableMethod hasDefaultMethodsInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).hasDefaultMethods();

    @Override
    public final boolean hasDefaultMethods() {
        return (boolean) handle(hasDefaultMethodsMethod, hasDefaultMethodsInvokable);
    }

    private static final SymbolicMethod declaresDefaultMethodsMethod = method("declaresDefaultMethods");
    private static final InvokableMethod declaresDefaultMethodsInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).declaresDefaultMethods();

    @Override
    public final boolean declaresDefaultMethods() {
        return (boolean) handle(declaresDefaultMethodsMethod, declaresDefaultMethodsInvokable);
    }

    public static final SymbolicMethod isAssignableFromMethod = method("isAssignableFrom", ResolvedJavaType.class);
    private static final InvokableMethod isAssignableFromInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isAssignableFrom((ResolvedJavaType) args[0]);

    @Override
    public final boolean isAssignableFrom(ResolvedJavaType other) {
        return (boolean) handle(isAssignableFromMethod, isAssignableFromInvokable, other);
    }

    public static final SymbolicMethod isInstanceMethod = method("isInstance", JavaConstant.class);
    private static final InvokableMethod isInstanceInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isInstance((JavaConstant) args[0]);

    @Override
    public final boolean isInstance(JavaConstant obj) {
        return (boolean) handle(isInstanceMethod, isInstanceInvokable, obj);
    }

    private static final SymbolicMethod getSuperclassMethod = method("getSuperclass");
    private static final InvokableMethod getSuperclassInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getSuperclass();

    @Override
    public final HotSpotResolvedObjectType getSuperclass() {
        return (HotSpotResolvedObjectType) handle(getSuperclassMethod, getSuperclassInvokable);
    }

    private static final SymbolicMethod getInterfacesMethod = method("getInterfaces");
    private static final InvokableMethod getInterfacesInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getInterfaces();

    @Override
    public final HotSpotResolvedObjectType[] getInterfaces() {
        return (HotSpotResolvedObjectType[]) handle(getInterfacesMethod, getInterfacesInvokable);
    }

    private static final SymbolicMethod getSingleImplementorMethod = method("getSingleImplementor");
    private static final InvokableMethod getSingleImplementorInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getSingleImplementor();

    @Override
    public final ResolvedJavaType getSingleImplementor() {
        return (ResolvedJavaType) handle(getSingleImplementorMethod, getSingleImplementorInvokable);
    }

    public static final SymbolicMethod findLeastCommonAncestorMethod = method("findLeastCommonAncestor", ResolvedJavaType.class);
    private static final InvokableMethod findLeastCommonAncestorInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).findLeastCommonAncestor((ResolvedJavaType) args[0]);

    @Override
    public final HotSpotResolvedObjectType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return (HotSpotResolvedObjectType) handle(findLeastCommonAncestorMethod, findLeastCommonAncestorInvokable, otherType);
    }

    public static final SymbolicMethod isPrimitiveMethod = method("isPrimitive");
    private static final InvokableMethod isPrimitiveInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isPrimitive();

    @Override
    public final boolean isPrimitive() {
        return (boolean) handle(isPrimitiveMethod, isPrimitiveInvokable);
    }

    public static final SymbolicMethod getJavaKindMethod = method("getJavaKind");
    public static final InvokableMethod getJavaKindInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getJavaKind();

    @Override
    public final JavaKind getJavaKind() {
        return (JavaKind) handle(getJavaKindMethod, getJavaKindInvokable);
    }

    private static final SymbolicMethod findUniqueConcreteMethodMethod = method("findUniqueConcreteMethod", ResolvedJavaMethod.class);
    private static final InvokableMethod findUniqueConcreteMethodInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).findUniqueConcreteMethod((ResolvedJavaMethod) args[0]);

    @Override
    @SuppressWarnings("unchecked")
    public final Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        return (Assumptions.AssumptionResult<ResolvedJavaMethod>) handle(findUniqueConcreteMethodMethod, findUniqueConcreteMethodInvokable, method);
    }

    public static final SymbolicMethod getInstanceFieldsMethod = method("getInstanceFields", boolean.class);
    public static final InvokableMethod getInstanceFieldsInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getInstanceFields((boolean) args[0]);

    @Override
    public final ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        return (ResolvedJavaField[]) handle(getInstanceFieldsMethod, getInstanceFieldsInvokable, includeSuperclasses);
    }

    public static final SymbolicMethod getStaticFieldsMethod = method("getStaticFields");
    public static final InvokableMethod getStaticFieldsInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getStaticFields();

    @Override
    public final ResolvedJavaField[] getStaticFields() {
        return (ResolvedJavaField[]) handle(getStaticFieldsMethod, getStaticFieldsInvokable);
    }

    private static final SymbolicMethod findInstanceFieldWithOffsetMethod = method("findInstanceFieldWithOffset", long.class, JavaKind.class);
    private static final InvokableMethod findInstanceFieldWithOffsetInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).findInstanceFieldWithOffset((long) args[0],
                    (JavaKind) args[1]);

    @Override
    public final ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        return (ResolvedJavaField) handle(findInstanceFieldWithOffsetMethod, findInstanceFieldWithOffsetInvokable, offset, expectedKind);
    }

    private static final SymbolicMethod getSourceFileNameMethod = method("getSourceFileName");
    private static final InvokableMethod getSourceFileNameInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getSourceFileName();

    @Override
    public final String getSourceFileName() {
        return (String) handle(getSourceFileNameMethod, getSourceFileNameInvokable);
    }

    private static final SymbolicMethod isLocalMethod = method("isLocal");
    private static final InvokableMethod isLocalInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isLocal();

    @Override
    public final boolean isLocal() {
        return (boolean) handle(isLocalMethod, isLocalInvokable);
    }

    private static final SymbolicMethod isMemberMethod = method("isMember");
    private static final InvokableMethod isMemberInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isMember();

    @Override
    public final boolean isMember() {
        return (boolean) handle(isMemberMethod, isMemberInvokable);
    }

    private static final SymbolicMethod getEnclosingTypeMethod = method("getEnclosingType");
    private static final InvokableMethod getEnclosingTypeInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getEnclosingType();

    @Override
    public final HotSpotResolvedObjectType getEnclosingType() {
        return (HotSpotResolvedObjectType) handle(getEnclosingTypeMethod, getEnclosingTypeInvokable);
    }

    private static final SymbolicMethod getDeclaredConstructorsMethod = method("getDeclaredConstructors");
    private static final InvokableMethod getDeclaredConstructorsInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getDeclaredConstructors();

    @Override
    public final ResolvedJavaMethod[] getDeclaredConstructors() {
        return (ResolvedJavaMethod[]) handle(getDeclaredConstructorsMethod, getDeclaredConstructorsInvokable);
    }

    private static final SymbolicMethod getDeclaredConstructorsBooleanMethod = method("getDeclaredConstructors", boolean.class);
    private static final InvokableMethod getDeclaredConstructorsBooleanInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getDeclaredConstructors((Boolean) args[0]);

    @Override
    public final ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        return (ResolvedJavaMethod[]) handle(getDeclaredConstructorsBooleanMethod, getDeclaredConstructorsBooleanInvokable, forceLink);
    }

    private static final SymbolicMethod getDeclaredMethodsMethod = method("getDeclaredMethods");
    private static final InvokableMethod getDeclaredMethodsInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getDeclaredMethods();

    @Override
    public final ResolvedJavaMethod[] getDeclaredMethods() {
        return (ResolvedJavaMethod[]) handle(getDeclaredMethodsMethod, getDeclaredMethodsInvokable);
    }

    private static final SymbolicMethod getAllMethodsMethod = method("getAllMethods", boolean.class);
    private static final InvokableMethod getAllMethodsInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getAllMethods((boolean) args[0]);

    @Override
    @SuppressWarnings("unchecked")
    public List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        return (List<ResolvedJavaMethod>) handle(getAllMethodsMethod, getAllMethodsInvokable);
    }

    private static final SymbolicMethod getDeclaredMethodsBooleanMethod = method("getDeclaredMethods", boolean.class);
    private static final InvokableMethod getDeclaredMethodsBooleanInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getDeclaredMethods((boolean) args[0]);

    @Override
    public final ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        return (ResolvedJavaMethod[]) handle(getDeclaredMethodsBooleanMethod, getDeclaredMethodsBooleanInvokable, forceLink);
    }

    private static final SymbolicMethod getClassInitializerMethod = method("getClassInitializer");
    private static final InvokableMethod getClassInitializerInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getClassInitializer();

    @Override
    public final ResolvedJavaMethod getClassInitializer() {
        return (ResolvedJavaMethod) handle(getClassInitializerMethod, getClassInitializerInvokable);
    }

    private static final SymbolicMethod isCloneableWithAllocationMethod = method("isCloneableWithAllocation");
    private static final InvokableMethod isCloneableWithAllocationInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).isCloneableWithAllocation();

    @Override
    public final boolean isCloneableWithAllocation() {
        return (boolean) handle(isCloneableWithAllocationMethod, isCloneableWithAllocationInvokable);
    }

    private static final SymbolicMethod lookupTypeMethod = method("lookupType", UnresolvedJavaType.class, boolean.class);
    private static final InvokableMethod lookupTypeInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).lookupType((UnresolvedJavaType) args[0], (Boolean) args[1]);

    @Override
    public final ResolvedJavaType lookupType(UnresolvedJavaType unresolvedJavaType, boolean resolve) {
        return (ResolvedJavaType) handle(lookupTypeMethod, lookupTypeInvokable, unresolvedJavaType, resolve);
    }

    private static final SymbolicMethod resolveFieldMethod = method("resolveField", UnresolvedJavaField.class, ResolvedJavaType.class);
    private static final InvokableMethod resolveFieldInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).resolveField((UnresolvedJavaField) args[0], (ResolvedJavaType) args[1]);

    @Override
    public final ResolvedJavaField resolveField(UnresolvedJavaField unresolvedJavaField, ResolvedJavaType accessingClass) {
        return (ResolvedJavaField) handle(resolveFieldMethod, resolveFieldInvokable, unresolvedJavaField, accessingClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return (T) handle(getAnnotationMethod, getAnnotationInvokable, annotationClass);
    }

    @Override
    public final Annotation[] getAnnotations() {
        return (Annotation[]) handle(getAnnotationsMethod, getAnnotationsInvokable);
    }

    @Override
    public final Annotation[] getDeclaredAnnotations() {
        return (Annotation[]) handle(getDeclaredAnnotationsMethod, getDeclaredAnnotationsInvokable);
    }

    @Override
    public final Object unproxify() {
        return handle(unproxifyMethod, unproxifyInvokable);
    }

    @Override
    public final int hashCode() {
        return (int) handle(hashCodeMethod, hashCodeInvokable);
    }

    public static final SymbolicMethod getJavaMirrorMethod = method("getJavaMirror");
    public static final InvokableMethod getJavaMirrorInvokable = (receiver, args) -> ((HotSpotResolvedJavaType) receiver).getJavaMirror();

    @Override
    public final JavaConstant getJavaMirror() {
        return (JavaConstant) handle(getJavaMirrorMethod, getJavaMirrorInvokable);
    }

    @Override
    public final boolean equals(Object obj) {
        return (boolean) handle(equalsMethod, equalsInvokable, obj);
    }

    @Override
    public final String toString() {
        return (String) handle(toStringMethod, toStringInvokable);
    }
}
