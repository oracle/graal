/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.reachability;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Reachability specific extension of AnalysisType. Contains mainly information necessary to resolve
 * virtual methods - instantiated subtypes and invoked virtual methods.
 */
public class ReachabilityAnalysisType extends AnalysisType {

    /**
     * All instantiated subtypes of this type.
     *
     * These sets can became huge, it would be nice to optimize it somehow ...maybe use TypeState?
     */
    private final Set<ReachabilityAnalysisType> instantiatedSubtypes = ConcurrentHashMap.newKeySet();

    private final Set<ReachabilityAnalysisMethod> invokedVirtualMethods = ConcurrentHashMap.newKeySet();

    private final Set<ReachabilityAnalysisMethod> invokedSpecialMethods = ConcurrentHashMap.newKeySet();

    public ReachabilityAnalysisType(AnalysisUniverse universe, ResolvedJavaType javaType, JavaKind storageKind, AnalysisType objectType, AnalysisType cloneableType) {
        super(universe, javaType, storageKind, objectType, cloneableType);
    }

    /** Register the type as instantiated with all its super types. */
    @Override
    protected void onInstantiated() {
        forAllSuperTypes(t -> ((ReachabilityAnalysisType) t).instantiatedSubtypes.add(this));
    }

    public Set<ReachabilityAnalysisType> getInstantiatedSubtypes() {
        return instantiatedSubtypes;
    }

    public Set<ReachabilityAnalysisMethod> getInvokedVirtualMethods() {
        return invokedVirtualMethods;
    }

    public void addInvokedVirtualMethod(ReachabilityAnalysisMethod method) {
        invokedVirtualMethods.add(method);
    }

    public void addSpecialInvokedMethod(ReachabilityAnalysisMethod method) {
        invokedSpecialMethods.add(method);
    }

    public Set<ReachabilityAnalysisMethod> getInvokedSpecialMethods() {
        return invokedSpecialMethods;
    }

    @Override
    public ReachabilityAnalysisMethod resolveConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return (ReachabilityAnalysisMethod) super.resolveConcreteMethod(method, callerType);
    }
}
