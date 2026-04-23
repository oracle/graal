/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicSet;

import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public final class BaseLayerMethodResolver implements BaseLayerType.MethodResolver {

    public interface BaseLayerProvider {
        List<PersistedMethod> getDeclaredMethods(BaseLayerType declaringType);

        int getBaseLayerTypeId(JavaType type);
    }

    public record PersistedMethod(String name, int modifiers, int returnTypeId, int[] argumentTypeIds, Supplier<ResolvedJavaMethod> methodSupplier) {
    }

    private final BaseLayerProvider provider;

    public BaseLayerMethodResolver(BaseLayerProvider provider) {
        this.provider = provider;
    }

    /**
     * {@link BaseLayerType} cannot use the normal {@code resolveConcreteMethod()} because it is not
     * complete. This helper first performs the method-selection step using persisted base-layer
     * metadata, then applies the normal {@code resolveConcreteMethod()} post-condition of
     * discarding abstract results.
     * <p>
     * The method-selection step follows JVMS {@code 5.4.6 Method Selection} in order:
     * <ol>
     * <li>If the resolved method is private, return it directly. Constructors are also returned
     * directly to preserve the surrounding JVMCI {@code resolveMethod()} contract.</li>
     * <li>Otherwise, walk the receiver class and then its superclasses looking for a matching
     * declaration that overrides the resolved method.</li>
     * <li>If no class method was selected and the resolved method comes from an interface, choose a
     * maximally-specific superinterface method set, using the same notion of maximal specificity as
     * JVMS {@code 5.4.3.3}, and then pick the unique non-abstract member if one exists.</li>
     * </ol>
     */
    @Override
    public ResolvedJavaMethod resolve(BaseLayerType resolvingType, ResolvedJavaMethod targetMethod, ResolvedJavaType ignoredCallerType) {
        /*
         * Like the normal JVMCI resolveMethod/resolveConcreteMethod contract, method selection only
         * applies to concrete receiver types.
         */
        if (resolvingType.isInterface() || !isSubtypeOf(resolvingType, targetMethod.getDeclaringClass())) {
            return null;
        }

        ResolvedJavaMethod selectedMethod = selectMethod(resolvingType, targetMethod);
        return selectedMethod == null || selectedMethod.isAbstract() ? null : selectedMethod;
    }

    private ResolvedJavaMethod selectMethod(ResolvedJavaType resolvingType, ResolvedJavaMethod targetMethod) {
        /*
         * JVMS 5.4.6 selects private methods directly, and constructors are treated as direct
         * targets by the surrounding JVMCI resolveMethod contract as well.
         */
        if (targetMethod.isPrivate() || targetMethod.isConstructor()) {
            return targetMethod;
        }

        ResolvedJavaMethod selectedMethod = selectClassMethod(resolvingType, targetMethod);
        if (selectedMethod != null || !targetMethod.getDeclaringClass().isInterface()) {
            return selectedMethod;
        }

        return selectInterfaceMethod(resolvingType, targetMethod);
    }

    /**
     * Step 2 of JVMS 5.4.6: walk the receiver class and then its superclasses looking for a
     * matching declaration. Exact holder matches are accepted directly and subtype matches use the
     * override relation from {@link #overridesTargetMethod(ResolvedJavaType, ResolvedJavaMethod)}.
     */
    private ResolvedJavaMethod selectClassMethod(ResolvedJavaType resolvingType, ResolvedJavaMethod targetMethod) {
        ResolvedJavaType currentType = resolvingType;
        while (currentType != null) {
            ResolvedJavaMethod method = findOverridingMethod(currentType, targetMethod);
            if (method != null) {
                return method;
            }
            currentType = currentType.getSuperclass();
        }
        return null;
    }

    /**
     * Step 3 of JVMS 5.4.6: if no class method was found, select a unique non-abstract
     * maximally-specific superinterface method when one exists. If multiple non-abstract
     * maximally-specific methods remain, we conservatively return {@code null}. If the
     * maximally-specific set contains only abstract methods, we return one of them and let the
     * outer {@code resolveConcreteMethod()} contract turn it into {@code null}.
     */
    private ResolvedJavaMethod selectInterfaceMethod(ResolvedJavaType resolvingType, ResolvedJavaMethod targetMethod) {
        EconomicSet<ResolvedJavaMethod> candidates = EconomicSet.create();
        collectInterfaceCandidates(resolvingType, targetMethod, candidates);
        if (candidates.isEmpty()) {
            return null;
        }

        EconomicSet<ResolvedJavaMethod> maximallySpecificMethods = maximallySpecificMethods(candidates);

        ResolvedJavaMethod uniqueNonAbstractMethod = null;
        for (ResolvedJavaMethod candidate : maximallySpecificMethods) {
            if (!candidate.isAbstract()) {
                if (uniqueNonAbstractMethod != null) {
                    return null;
                }
                uniqueNonAbstractMethod = candidate;
            }
        }
        return uniqueNonAbstractMethod != null ? uniqueNonAbstractMethod : maximallySpecificMethods.toList().getFirst();
    }

    private EconomicSet<ResolvedJavaMethod> maximallySpecificMethods(EconomicSet<ResolvedJavaMethod> candidates) {
        /*
         * JVMS 5.4.3.3 defines the maximally-specific superinterface methods as the methods whose
         * declaring interfaces are not strict supertypes of any other candidate's declaring
         * interface. We maintain the current local maxima by removing less specific methods when a
         * more specific candidate is encountered.
         */
        EconomicSet<ResolvedJavaMethod> currentMaximallySpecificMethods = EconomicSet.create();
        for (ResolvedJavaMethod candidate : candidates) {
            if (isLocalMaximallySpecific(candidate, currentMaximallySpecificMethods)) {
                currentMaximallySpecificMethods.add(candidate);
            }
        }
        return currentMaximallySpecificMethods;
    }

    private boolean isLocalMaximallySpecific(ResolvedJavaMethod candidate, EconomicSet<ResolvedJavaMethod> currentMaximallySpecificMethods) {
        var iterator = currentMaximallySpecificMethods.iterator();
        while (iterator.hasNext()) {
            ResolvedJavaMethod currentMaximallySpecificMethod = iterator.next();
            ResolvedJavaMethod moreSpecificMethod = moreSpecificMethod(candidate, currentMaximallySpecificMethod);
            if (candidate.equals(moreSpecificMethod)) {
                iterator.remove();
            } else if (currentMaximallySpecificMethod.equals(moreSpecificMethod)) {
                return false;
            }
        }
        return true;
    }

    private ResolvedJavaMethod moreSpecificMethod(ResolvedJavaMethod firstMethod, ResolvedJavaMethod secondMethod) {
        if (isSubtypeOf(firstMethod.getDeclaringClass(), secondMethod.getDeclaringClass())) {
            return firstMethod;
        }
        if (isSubtypeOf(secondMethod.getDeclaringClass(), firstMethod.getDeclaringClass())) {
            return secondMethod;
        }
        return null;
    }

    private void collectInterfaceCandidates(ResolvedJavaType type, ResolvedJavaMethod targetMethod, EconomicSet<ResolvedJavaMethod> candidates) {
        if (type == null) {
            return;
        }

        for (ResolvedJavaType interfaceType : type.getInterfaces()) {
            ResolvedJavaMethod method = findDeclaredMethod(interfaceType, targetMethod);
            if (method != null) {
                candidates.add(method);
            }

            /*
             * JVMS 5.4.3.3 computes maximally-specific methods over the transitive superinterface
             * graph, not only the directly implemented interfaces.
             */
            collectInterfaceCandidates(interfaceType, targetMethod, candidates);
        }
        collectInterfaceCandidates(type.getSuperclass(), targetMethod, candidates);
    }

    private ResolvedJavaMethod findDeclaredBaseMethod(BaseLayerType declaringType, ResolvedJavaMethod targetMethod) {
        for (PersistedMethod persistedMethod : provider.getDeclaredMethods(declaringType)) {
            if (!sameSignature(persistedMethod, targetMethod)) {
                continue;
            }
            int modifiers = persistedMethod.modifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers)) {
                continue;
            }
            return persistedMethod.methodSupplier().get();
        }
        return null;
    }

    private boolean sameSignature(PersistedMethod persistedMethod, ResolvedJavaMethod targetMethod) {
        if (!persistedMethod.name().equals(targetMethod.getName())) {
            return false;
        }

        Signature signature = targetMethod.getSignature();
        if (persistedMethod.argumentTypeIds().length != signature.getParameterCount(false)) {
            return false;
        }

        for (int i = 0; i < persistedMethod.argumentTypeIds().length; i++) {
            if (persistedMethod.argumentTypeIds()[i] != provider.getBaseLayerTypeId(signature.getParameterType(i, targetMethod.getDeclaringClass()))) {
                return false;
            }
        }

        return persistedMethod.returnTypeId() == provider.getBaseLayerTypeId(signature.getReturnType(targetMethod.getDeclaringClass()));
    }

    private ResolvedJavaMethod findDeclaredMethod(ResolvedJavaType declaringType, ResolvedJavaMethod targetMethod) {
        if (declaringType instanceof BaseLayerType baseLayerType) {
            return findDeclaredBaseMethod(baseLayerType, targetMethod);
        }
        return declaringType.findMethod(targetMethod.getName(), targetMethod.getSignature());
    }

    private ResolvedJavaMethod findOverridingMethod(ResolvedJavaType declaringType, ResolvedJavaMethod targetMethod) {
        ResolvedJavaMethod declaredMethod = findDeclaredMethod(declaringType, targetMethod);
        if (declaredMethod == null) {
            return null;
        }
        if (declaringType.equals(targetMethod.getDeclaringClass())) {
            return declaredMethod;
        }
        return overridesTargetMethod(declaringType, targetMethod) ? declaredMethod : null;
    }

    /**
     * JVMS 5.4.5 override relation used by step 2 above. This helper operates on an already
     * selected declared candidate method. Package-private methods use the transitive
     * intermediate-superclass rule from JVMS {@code 5.4.5}. The {@code canBeStaticallyBound()}
     * check is the additional JVMCI/SVM constraint already used by the normal
     * {@code resolveConcreteMethod()} path.
     */
    private boolean overridesTargetMethod(ResolvedJavaType declaringType, ResolvedJavaMethod targetMethod) {
        ResolvedJavaType targetDeclaringType = targetMethod.getDeclaringClass();
        if (!isSubtypeOf(declaringType, targetDeclaringType)) {
            return false;
        }
        if (targetMethod.canBeStaticallyBound()) {
            return false;
        }
        if (!targetMethod.isPackagePrivate()) {
            return true;
        }
        if (isSamePackage(declaringType, targetDeclaringType)) {
            return true;
        }

        /*
         * JVMS 5.4.5 allows package-private overriding to flow through an intermediate declaration
         * m_B only when both halves of the chain hold: m_B overrides m_A, and the current
         * declaration m_C overrides m_B.
         */
        ResolvedJavaType currentSuper = declaringType.getSuperclass();
        while (currentSuper != null && !currentSuper.equals(targetDeclaringType)) {
            ResolvedJavaMethod intermediateMethod = findDeclaredMethod(currentSuper, targetMethod);
            if (intermediateMethod != null &&
                            overridesTargetMethod(currentSuper, targetMethod) &&
                            overridesTargetMethod(declaringType, intermediateMethod)) {
                return true;
            }
            currentSuper = currentSuper.getSuperclass();
        }
        return false;
    }

    private boolean isSubtypeOf(ResolvedJavaType type, ResolvedJavaType superType) {
        if (type == null) {
            return false;
        }
        if (type.equals(superType)) {
            return true;
        }
        if (isSubtypeOf(type.getSuperclass(), superType)) {
            return true;
        }
        for (ResolvedJavaType interfaceType : type.getInterfaces()) {
            if (isSubtypeOf(interfaceType, superType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSamePackage(ResolvedJavaType type, ResolvedJavaType otherType) {
        return JVMCIReflectionUtil.getPackageName(type).equals(JVMCIReflectionUtil.getPackageName(otherType));
    }
}
