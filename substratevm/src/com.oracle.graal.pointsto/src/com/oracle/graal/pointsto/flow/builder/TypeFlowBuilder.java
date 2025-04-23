/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow.builder;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AlwaysEnabledPredicateFlow;
import com.oracle.graal.pointsto.flow.PredicateMergeFlow;
import com.oracle.graal.pointsto.flow.PrimitiveFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.PointsToStats;

import jdk.graal.compiler.phases.common.LazyValue;

/**
 * The type flow builder is a node in the type flow builder graph. The {@link #useDependencies} and
 * {@link #observerDependencies} are links to inputs from a data flow perspective, i.e., they are
 * the reverse links of uses and observers for the built type flow.
 * 
 * The {@link TypeFlowBuilder} use dependency link: <code>flowBuilder0 <- flowBuilder1</code> will
 * result in the {@link TypeFlow} use link: <code>flow0 -> flow1</code>.
 */
public final class TypeFlowBuilder<T extends TypeFlow<?>> {

    public static <U extends TypeFlow<?>> TypeFlowBuilder<U> create(PointsToAnalysis bb, PointsToAnalysisMethod method, Object predicate, Object source, Class<U> clazz, Supplier<U> supplier) {
        TypeFlowBuilder<U> builder = new TypeFlowBuilder<>(source, predicate, clazz, new LazyValue<>(supplier));
        assert checkForPrimitiveFlows(bb, clazz) : "Primitive flow encountered without -H:+TrackPrimitiveValues: " + clazz + " in method " + method.getQualifiedName();
        assert checkPredicate(bb, clazz, predicate) : "Null or invalid predicate " + predicate + "  encountered with -H:+UsePredicates: " + clazz + " in method " + method.getQualifiedName();
        PointsToStats.registerTypeFlowBuilder(bb, builder);
        return builder;
    }

    /**
     * A sanity check. If tracking primitive values is disabled, no primitive flows should be
     * created.
     */
    private static <U extends TypeFlow<?>> boolean checkForPrimitiveFlows(PointsToAnalysis bb, Class<U> clazz) {
        return bb.trackPrimitiveValues() || !PrimitiveFlow.class.isAssignableFrom(clazz);
    }

    /**
     * A sanity check. If predicates are enabled, the predicate object should be: a) null for
     * AlwaysEnabledPredicateFlow, b) List of TypeFlowBuilders for PredicateMergeFlow, c)
     * TypeFlowBuilder otherwise.
     */
    private static boolean checkPredicate(PointsToAnalysis bb, Class<?> clazz, Object predicate) {
        if (!bb.usePredicates()) {
            assert predicate == null : "Predicates are disabled: " + predicate;
            return true;
        }
        if (clazz == AlwaysEnabledPredicateFlow.class) {
            return predicate == null;
        }
        if (clazz == PredicateMergeFlow.class) {
            return predicate instanceof List<?>;
        }
        return predicate instanceof TypeFlowBuilder<?>;
    }

    private final Object source;

    private final Class<T> flowClass;
    private final LazyValue<T> lazyTypeFlowCreator;
    /** Input dependency, i.e., builders that have this builder as an use. */
    private final Set<TypeFlowBuilder<?>> useDependencies;
    /** Input dependency, i.e., builders that have this builder as an observer. */
    private final Set<TypeFlowBuilder<?>> observerDependencies;
    private boolean buildingAnActualParameter;
    private boolean isMaterialized;

    /**
     * Predicate dependency. Most often, it will be a single type flow builder, apart from
     * PredicateMergeFlow, which has a list of type flow builders as predicates (one for each input
     * branch), or AlwaysEnabledPredicateFlow, which has no predicate.
     */
    private final Object predicate;

    private TypeFlowBuilder(Object source, Object predicate, Class<T> flowClass, LazyValue<T> creator) {
        this.flowClass = flowClass;
        this.source = source;
        this.lazyTypeFlowCreator = creator;
        this.useDependencies = new HashSet<>();
        this.observerDependencies = new HashSet<>();
        this.buildingAnActualParameter = false;
        this.isMaterialized = false;
        this.predicate = predicate;
    }

    public void markAsBuildingAnActualParameter() {
        this.buildingAnActualParameter = true;
    }

    public boolean isBuildingAnActualParameter() {
        return buildingAnActualParameter;
    }

    public boolean isMaterialized() {
        return isMaterialized;
    }

    public Class<T> getFlowClass() {
        return flowClass;
    }

    public Object getSource() {
        return source;
    }

    public void addUseDependency(TypeFlowBuilder<?> dependency) {
        this.useDependencies.add(dependency);
    }

    Collection<TypeFlowBuilder<?>> getUseDependencies() {
        return useDependencies;
    }

    public void addObserverDependency(TypeFlowBuilder<?> dependency) {
        this.observerDependencies.add(dependency);
    }

    Collection<TypeFlowBuilder<?>> getObserverDependencies() {
        return observerDependencies;
    }

    public Object getPredicate() {
        return predicate;
    }

    public T get() {
        T value = lazyTypeFlowCreator.get();
        isMaterialized = true;
        return value;
    }

}
