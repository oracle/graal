/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.classinitialization;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Keeps a type-hierarchy dependency graph for {@link AnalysisType}s from {@code universe}. Each
 * type carries the information if it {@link Safety#SAFE} or {@link Safety#UNSAFE} to execute during
 * native-image generation or class eager initialization optimization for OpenJDK.
 * 
 * The algorithm assigns all types ( {@link #initTypeSafety}) and all methods (
 * {@link #initialMethodSafety}) with their initial safety.
 * 
 * Then the information about unsafety is iteratively propagated through the graph in
 * {@link #computeInitializerSafety}.
 */
public abstract class AbstractTypeInitializerGraph {
    public enum Safety {
        SAFE,
        UNSAFE,
    }

    protected final HostVM hostVM;

    protected final Map<AnalysisType, Safety> types = new HashMap<>();
    protected final Map<AnalysisType, Set<AnalysisType>> dependencies = new HashMap<>();

    protected final Map<AnalysisMethod, Safety> methodSafety = new HashMap<>();
    private Collection<AnalysisMethod> methods;
    private final AnalysisUniverse universe;

    public AbstractTypeInitializerGraph(AnalysisUniverse universe) {
        this.universe = universe;
        hostVM = universe.hostVM();
    }

    /**
     * Iteratively propagate information about unsafety through the methods (
     * {@link #updateMethodSafety}) and the initializer graph (
     * {@link #updateTypeInitializerSafety()}).
     */
    public void computeInitializerSafety() {
        universe.getTypes().forEach(this::addInitializer);
        universe.getTypes().forEach(this::addInitializerDependencies);
        /* initialize all methods with original safety data */
        methods = universe.getMethods();
        methods.stream().filter(AnalysisMethod::isImplementationInvoked).forEach(m -> methodSafety.put(m, initialMethodSafety(m)));

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

    public boolean isUnsafe(AnalysisType type) {
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
     * invokes are unsafe {@link AbstractTypeInitializerGraph#isInvokeInitiallyUnsafe}.
     *
     * Substituted methods are unsafe because their execution at image-build time would initialize
     * types unknown to points-to analysis (which sees only the substituted version.
     */
    protected Safety initialMethodSafety(AnalysisMethod m) {
        for (var invoke : m.getInvokes()) {
            if (isInvokeInitiallyUnsafe(invoke)) {
                return Safety.UNSAFE;
            }
        }
        return cLInitHasSideEffect(m) ? Safety.UNSAFE : Safety.SAFE;
    }

    /**
     * Unsafe invokes (1) call native methods, and/or (2) can't be statically bound.
     */
    private boolean isInvokeInitiallyUnsafe(InvokeInfo i) {
        return isNativeInvoke(i.getTargetMethod()) || !canBeStaticallyBound(i);
    }

    protected boolean cLInitHasSideEffect(AnalysisMethod method) {
        return hostVM.hasClassInitializerSideEffect(method);
    }

    protected boolean isNativeInvoke(AnalysisMethod method) {
        return method.isNative();
    }

    protected boolean canBeStaticallyBound(InvokeInfo invokeInfo) {
        return invokeInfo.canBeStaticallyBound();
    }

    /**
     * Type is promoted to unsafe when it is not already unsafe and it (1) depends on an unsafe
     * type, or (2) its class initializer was promoted to unsafe.
     *
     * @return if promotion to unsafe happened
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
    protected boolean updateMethodSafety(AnalysisMethod m) {
        assert methodSafety.get(m) == Safety.SAFE;
        for (var invoke : m.getInvokes()) {
            if (isInvokeUnsafeIterative(invoke)) {
                methodSafety.put(m, Safety.UNSAFE);
                return true;
            }
        }
        if (initUnsafeClass(m).isPresent()) {
            methodSafety.put(m, Safety.UNSAFE);
            return true;
        }
        return false;
    }

    protected Optional<AnalysisType> initUnsafeClass(AnalysisMethod m) {
        return hostVM.getInitializedClasses(m).stream().filter(this::isUnsafe).findAny();
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
        for (AnalysisMethod callee : i.getOriginalCallees()) {
            if (methodSafety.get(callee) == Safety.UNSAFE) {
                return true;
            }
        }
        return false;
    }

    private void addInitializer(AnalysisType t) {
        initTypeSafety(t);
        dependencies.put(t, new HashSet<>());
    }

    protected abstract void initTypeSafety(AnalysisType t);

    public Set<AnalysisType> getDependencies(AnalysisType type) {
        return Collections.unmodifiableSet(dependencies.get(type));
    }

    public Map<AnalysisType, Safety> getTypes() {
        return types;
    }

    public boolean isSafeMethod(AnalysisMethod m) {
        return Safety.SAFE == methodSafety.get(m);
    }
}
