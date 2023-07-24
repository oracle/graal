/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaConstant;

/**
 * A type whose class initializer is analyzed as part of the same
 * {@link SimulateClassInitializerCluster} because there is a possible cyclic dependency.
 * 
 * See {@link SimulateClassInitializerSupport} for an overview of class initializer simulation.
 */
public final class SimulateClassInitializerClusterMember {
    final SimulateClassInitializerCluster cluster;
    final AnalysisType type;

    final EconomicSet<SimulateClassInitializerClusterMember> dependencies = EconomicSet.create();
    final List<Object> notInitializedReasons = new ArrayList<>();
    final EconomicMap<AnalysisField, JavaConstant> staticFieldValues = EconomicMap.create();

    /** The mutable status field of the cluster member. */
    SimulateClassInitializerStatus status;

    SimulateClassInitializerClusterMember(SimulateClassInitializerCluster cluster, AnalysisType type) {
        this.cluster = cluster;
        this.type = type;
        cluster.clusterMembers.put(type, this);

        this.status = SimulateClassInitializerStatus.COLLECTING_DEPENDENCIES;
    }
}
