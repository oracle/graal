/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;

import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.nativeimage.AnnotationAccess;

/**
 * Keeps a type-hierarchy dependency graph for {@link AnalysisType}s from {@code universe}. Each
 * type carries the information if it {@link Safety#SAFE} or {@link Safety#UNSAFE} to execute during
 * native-image generation.
 *
 * The algorithm assigns all types ( {@link #initialTypeInitializerSafety}) and all methods (
 * {@link #initialMethodSafety}) with their initial safety.
 *
 * Then the information about unsafety is iteratively propagated through the graph in
 * {@link #computeInitializerSafety}.
 *
 * NOTE: the dependency between methods and type initializers is maintained by the
 * {@link SubstrateClassInitializationPlugin} that emits {@link EnsureClassInitializedNode} for
 * every load, store, call, and instantiation in the bytecode. These dependencies are collected in
 * {@link SVMHost#getInitializedClasses}.
 */
final class TypeInitializerGraph {
    private final SVMHost hostVM;
    private final ProvenSafeClassInitializationSupport classInitializationSupport;

    private enum Safety {
        SAFE,
        UNSAFE,
    }

    private final Map<AnalysisType, Safety> types = new HashMap<>();
    private final Map<AnalysisType, Set<AnalysisType>> dependencies = new HashMap<>();

    private final Map<AnalysisMethod, Safety> methodSafety = new HashMap<>();
    private final Collection<AnalysisMethod> methods;

    TypeInitializerGraph(ProvenSafeClassInitializationSupport classInitializationSupport, AnalysisUniverse universe) {
        hostVM = ((SVMHost) universe.hostVM());
        this.classInitializationSupport = classInitializationSupport;

        universe.getTypes().forEach(this::addInitializer);
        universe.getTypes().forEach(this::addInitializerDependencies);
        /* initialize all methods with original safety data */
        methods = universe.getMethods();
        methods.stream().filter(AnalysisMethod::isImplementationInvoked).forEach(m -> methodSafety.put(m, initialMethodSafety(m)));
    }

    /**
     * Iteratively propagate information about unsafety through the methods (
     * {@link #updateMethodSafety}) and the initializer graph (
     * {@link #updateTypeInitializerSafety()}).
     */
    void computeInitializerSafety() {
        boolean newPromotions;
        do {
            AtomicBoolean methodSafetyChanged = new AtomicBoolean(false);
            methods.stream().filter(m -> methodSafety.get(m) == Safety.SAFE)
                            .forEach(m -> {
                                if (updateMethodSafety(m)) {
                                    methodSafetyChanged.set(true);
                                }
                            });
            newPromotions = methodSafetyChanged.get() || updateTypeInitializerSafety();
        } while (newPromotions);
    }

    /**
     * A type initializer is initially unsafe only if it was marked by the user as such.
     */
    private Safety initialTypeInitializerSafety(AnalysisType t) {
        return classInitializationSupport.specifiedInitKindFor(t.getJavaClass()) == InitKind.BUILD_TIME || classInitializationSupport.canBeProvenSafe(t.getJavaClass())
                        ? Safety.SAFE
                        : Safety.UNSAFE;
    }

    boolean isUnsafe(AnalysisType type) {
        return types.get(type) == Safety.UNSAFE;
    }

    public void setUnsafe(AnalysisType t) {
        types.put(t, Safety.UNSAFE);
    }

    private boolean updateTypeInitializerSafety() {
        Set<AnalysisType> unsafeOrProcessedTypes = types.entrySet().stream()
                        .filter(t -> t.getValue() == Safety.UNSAFE)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());

        return types.keySet().stream()
                        .map(t -> tryPromoteToUnsafe(t, unsafeOrProcessedTypes))
                        .reduce(false, (lhs, rhs) -> lhs || rhs);
    }

    private void addInitializerDependencies(AnalysisType t) {
        addInterfaceDependencies(t, t.getInterfaces());
        if (t.getSuperclass() != null) {
            addDependency(t, t.getSuperclass());
        }
    }

    private void addInterfaceDependencies(AnalysisType t, AnalysisType[] interfaces) {
        for (AnalysisType anInterface : interfaces) {
            if (anInterface.declaresDefaultMethods()) {
                addDependency(t, anInterface);
            }
            addInterfaceDependencies(t, anInterface.getInterfaces());
        }
    }

    private void addDependency(AnalysisType dependent, AnalysisType dependee) {
        dependencies.get(dependent).add(dependee);
    }

    /**
     * Method is considered initially unsafe if (1) it is a substituted method, or (2) if any of the
     * invokes are unsafe {@link TypeInitializerGraph#isInvokeInitiallyUnsafe}.
     *
     * Substituted methods are unsafe because their execution at image-build time would initialize
     * types unknown to points-to analysis (which sees only the substituted version.
     */
    private Safety initialMethodSafety(AnalysisMethod m) {
        for (var invoke : m.getInvokes()) {
            if (isInvokeInitiallyUnsafe(invoke)) {
                return Safety.UNSAFE;
            }
        }
        return hostVM.hasClassInitializerSideEffect(m) ||
                        isSubstitutedMethod(m) ? Safety.UNSAFE : Safety.SAFE;
    }

    private boolean isSubstitutedMethod(AnalysisMethod m) {
        return !classInitializationSupport.shouldInitializeAtRuntime(m.getDeclaringClass()) && m.getWrapped() instanceof SubstitutionMethod;
    }

    /**
     * Unsafe invokes (1) call native methods, and/or (2) can't be statically bound.
     */
    private static boolean isInvokeInitiallyUnsafe(InvokeInfo i) {
        return i.getTargetMethod().isNative() ||
                        !i.canBeStaticallyBound();
    }

    /**
     * Type is promoted to unsafe when it is not already unsafe and it (1) depends on an unsafe
     * type, or (2) its class initializer was promoted to unsafe.
     *
     * @return if pomotion to unsafe happened
     */
    private boolean tryPromoteToUnsafe(AnalysisType type, Set<AnalysisType> unsafeOrProcessed) {
        if (unsafeOrProcessed.contains(type)) {
            return false;
        } else {
            unsafeOrProcessed.add(type);
            if ((type.getClassInitializer() != null && methodSafety.get(type.getClassInitializer()) == Safety.UNSAFE) ||
                            dependencies.get(type).stream().anyMatch(t -> types.get(t) == Safety.UNSAFE || tryPromoteToUnsafe(t, unsafeOrProcessed))) {
                setUnsafe(type);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * A method is unsafe if (1) any of it's invokes are unsafe or (2) the method depends on an
     * unsafe class initializer.
     */
    private boolean updateMethodSafety(AnalysisMethod m) {
        assert methodSafety.get(m) == Safety.SAFE;
        for (var invoke : m.getInvokes()) {
            if (isInvokeUnsafeIterative(invoke)) {
                methodSafety.put(m, Safety.UNSAFE);
                return true;
            }
        }
        if (hostVM.getInitializedClasses(m).stream().anyMatch(this::isUnsafe)) {
            methodSafety.put(m, Safety.UNSAFE);
            return true;
        }
        return false;
    }

    /**
     * Invoke becomes unsafe if it calls other unsafe methods.
     */
    private boolean isInvokeUnsafeIterative(InvokeInfo i) {
        /*
         * Note that even though (for now) we only process invokes that can be statically bound, we
         * cannot just take the target method of the type flow: the static analysis can
         * de-virtualize the target method to a method overridden in a subclass. So we must look at
         * the actual callees of the type flow, even though we know that there is at most one callee
         * returned.
         */
        for (AnalysisMethod callee : i.getCallees()) {
            if (methodSafety.get(callee) == Safety.UNSAFE) {
                return true;
            }
        }
        return false;
    }

    private void addInitializer(AnalysisType t) {
        ResolvedJavaType rt = t.getWrappedWithoutResolve();
        boolean isSubstituted = false;
        if (rt instanceof SubstitutionType) {
            SubstitutionType substitutionType = (SubstitutionType) rt;
            isSubstituted = AnnotationAccess.isAnnotationPresent(substitutionType, Substitute.class) || AnnotationAccess.isAnnotationPresent(substitutionType, Delete.class);
        }
        types.put(t, isSubstituted ? Safety.UNSAFE : initialTypeInitializerSafety(t));
        dependencies.put(t, new HashSet<>());
    }

    Set<AnalysisType> getDependencies(AnalysisType type) {
        return Collections.unmodifiableSet(dependencies.get(type));
    }

}
