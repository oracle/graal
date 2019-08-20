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

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.typestate.PointsToStats;
import org.graalvm.compiler.nodes.ParameterNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;

public class TypeFlowGraphBuilder {
    private final BigBang bb;
    /**
     * The data flow sink builders are the nodes that should not be removed. They are data flow
     * sinks in the context of type flows graph that it creates, i.e., they collect useful
     * information for the analysis. Through the TypeFlowBuilder.useDependencies and
     * TypeFlowBuilder.observerDependencies links these nodes reach to their inputs, transitively,
     * and determine all the nodes that they depend on and must be retained.
     */
    private final List<TypeFlowBuilder<?>> dataFlowSinkBuilders;

    public TypeFlowGraphBuilder(BigBang bb) {
        this.bb = bb;
        dataFlowSinkBuilders = new ArrayList<>();
    }

    /**
     * Register a type flow builder as a sink, i.e., a builder for a flow that should not be removed
     * since it is a leaf in the data flow graph. These are actual parameters (i.e., values passed
     * as parameters for calls), field stores, indexed loads, all invokes, etc.
     */
    public void registerSinkBuilder(TypeFlowBuilder<?> sinkBuilder) {
        dataFlowSinkBuilders.add(sinkBuilder);
    }

    /**
     * A list of method for which we want to retain the parameters since they are needed for
     * collecting wait/notify field info and hash code field info.
     */
    private static final List<String> waitNotifyHashCodeMethods = new ArrayList<>();
    static {
        try {
            Method wait = Object.class.getMethod("wait", long.class);
            waitNotifyHashCodeMethods.add(format(wait));

            Method notify = Object.class.getMethod("notify");
            waitNotifyHashCodeMethods.add(format(notify));

            Method notifyAll = Object.class.getMethod("notifyAll");
            waitNotifyHashCodeMethods.add(format(notifyAll));

            Method hashCode = System.class.getMethod("identityHashCode", Object.class);
            waitNotifyHashCodeMethods.add(format(hashCode));
        } catch (NoSuchMethodException e) {
            throw AnalysisError.shouldNotReachHere(e);
        }
    }

    /** Format a reflection method using the same format as JavaMethod.format("%H.%n(%p)"). */
    private static String format(Method m) {
        return m.getDeclaringClass().getName() + "." + m.getName() +
                        "(" + Arrays.stream(m.getParameterTypes()).map(Class::getName).collect(Collectors.joining(", ")) + ")";

    }

    /**
     * Check if the formal parameter is a parameter of one of the wait/notify/hashCode methods. If
     * so add it as a sink since it must be retained. Don't need to check the position of the
     * parameter, since each of the checked methods has at most one object parameter.
     */
    public void checkFormalParameterBuilder(TypeFlowBuilder<?> paramBuilder) {
        AnalysisMethod method = (AnalysisMethod) ((ParameterNode) paramBuilder.getSource()).graph().method();
        String methodFormat = method.getQualifiedName();
        for (String specialMethodFormat : waitNotifyHashCodeMethods) {
            if (methodFormat.equals(specialMethodFormat)) {
                dataFlowSinkBuilders.add(paramBuilder);
            }
        }
    }

    /**
     * Materialize all reachable flows starting from the sinks and working backwards following the
     * dependency chains. Unreachable flows will be implicitly pruned.
     */
    public void build() {
        /* Work queue used by the iterative graph traversal. */
        HashSet<TypeFlowBuilder<?>> processed = new HashSet<>();
        ArrayDeque<TypeFlowBuilder<?>> workQueue = new ArrayDeque<>();

        /* Keep track of already materialized flows. */
        for (TypeFlowBuilder<?> sinkBuilder : dataFlowSinkBuilders) {
            if (processed.contains(sinkBuilder)) {
                /*
                 * This sink has already been processed; probably reached from another sink through
                 * the dependency chain. This is possible since the sink registration is
                 * conservative, i.e., it can register a builder as a sink even if it can be reached
                 * from another sink through the dependency chain.
                 */
                continue;
            }

            workQueue.addLast(sinkBuilder);
            while (!workQueue.isEmpty()) {
                TypeFlowBuilder<?> builder = workQueue.removeFirst();
                /* Materialize the builder. */
                TypeFlow<?> flow = builder.get();

                /* The retain reason is the sink from which it was reached. */
                PointsToStats.registerTypeFlowRetainReason(bb, flow, (sinkBuilder.isBuildingAnActualParameter() ? "ActualParam=" : "") + sinkBuilder.getFlowClass().getSimpleName());

                /* Mark the builder as materialized. */
                processed.add(builder);

                /*
                 * Iterate over use and observer dependencies. Add them to the workQueue only if
                 * they have not been already processed.
                 */
                for (TypeFlowBuilder<?> useDependency : builder.getUseDependencies()) {
                    if (!processed.contains(useDependency)) {
                        workQueue.addLast(useDependency);
                    }
                    TypeFlow<?> useFlow = useDependency.get();
                    /* Convert the use dependency into a use data flow. */
                    useFlow.addOriginalUse(bb, flow);
                }
                for (TypeFlowBuilder<?> observerDependency : builder.getObserverDependencies()) {
                    if (!processed.contains(observerDependency)) {
                        workQueue.addLast(observerDependency);
                    }
                    TypeFlow<?> observerFlow = observerDependency.get();
                    /* Convert the observer dependency into an observer data flow. */
                    observerFlow.addOriginalObserver(bb, flow);
                }

            }
        }
    }
}
