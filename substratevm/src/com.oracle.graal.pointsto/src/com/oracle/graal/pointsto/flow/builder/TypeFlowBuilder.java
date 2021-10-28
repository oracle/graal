/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;
import java.util.function.Supplier;

import org.graalvm.compiler.phases.common.LazyValue;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.typestate.PointsToStats;

/**
 * The type flow builder is a node in the type flow builder graph. The {@link #useDependencies} and
 * {@link #observerDependencies} are links to inputs from a data flow perspective, i.e., they are
 * the reverse links of uses and observers for the built type flow.
 * 
 * The {@link TypeFlowBuilder} use dependency link: <code>flowBuilder0 <- flowBuilder1</code> will
 * result in the {@link TypeFlow} use link: <code>flow0 -> flow1</code>.
 */
public final class TypeFlowBuilder<T extends TypeFlow<?>> {

    public static <U extends TypeFlow<?>> TypeFlowBuilder<U> create(PointsToAnalysis bb, Object source, Class<U> clazz, Supplier<U> supplier) {
        TypeFlowBuilder<U> builder = new TypeFlowBuilder<>(source, clazz, new LazyValue<>(supplier));
        PointsToStats.registerTypeFlowBuilder(bb, builder);
        return builder;
    }

    private final Object source;

    private final Class<T> flowClass;
    private final LazyValue<T> lazyTypeFlowCreator;
    /** Input dependency, i.e., builders that have this builder as an use. */
    private final Set<TypeFlowBuilder<?>> useDependencies;
    /** Input dependency, i.e., builders that have this builder as an observer. */
    private final Set<TypeFlowBuilder<?>> observerDependencies;
    private boolean buildingAnActualParameter;
    private boolean buildingAnActualReceiver;
    private boolean isMaterialized;

    private TypeFlowBuilder(Object source, Class<T> flowClass, LazyValue<T> creator) {
        this.flowClass = flowClass;
        this.source = source;
        this.lazyTypeFlowCreator = creator;
        this.useDependencies = new HashSet<>();
        this.observerDependencies = new HashSet<>();
        this.buildingAnActualParameter = false;
        this.buildingAnActualReceiver = false;
        this.isMaterialized = false;
    }

    public void markAsBuildingAnActualParameter() {
        this.buildingAnActualParameter = true;
    }

    public boolean isBuildingAnActualParameter() {
        return buildingAnActualParameter;
    }

    public void markAsBuildingAnActualReceiver() {
        this.buildingAnActualReceiver = true;
    }

    public boolean isBuildingAnActualReceiver() {
        return buildingAnActualReceiver;
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

    public T get() {
        T value = lazyTypeFlowCreator.get();
        isMaterialized = true;
        value.setUsedAsAParameter(buildingAnActualParameter);
        value.setUsedAsAReceiver(buildingAnActualReceiver);
        return value;
    }

}
