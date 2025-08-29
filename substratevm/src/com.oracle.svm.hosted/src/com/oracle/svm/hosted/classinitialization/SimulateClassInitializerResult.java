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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.graal.pointsto.meta.AnalysisField;

import jdk.vm.ci.meta.JavaConstant;

/**
 * The simulation result for a class. If a class was simulated as initialized, it also stores the
 * static field values for the class, i.e., the initial values of the static fields that are later
 * on written into the image heap.
 * 
 * See {@link SimulateClassInitializerSupport} for an overview of class initializer simulation.
 */
final class SimulateClassInitializerResult {

    /** We didn't try simulation either because the feature is disabled or type's linking failed. */
    static final SimulateClassInitializerResult NOT_SIMULATED_INITIALIZED = new SimulateClassInitializerResult(false, null);
    /** We tried simulating the type's initializer but failed. */
    static final SimulateClassInitializerResult FAILED_SIMULATED_INITIALIZED = new SimulateClassInitializerResult(false, null);
    /** Type was already initialized in the host VM. We didn't try to simulate it. */
    static final SimulateClassInitializerResult INITIALIZED_HOSTED = SimulateClassInitializerResult.forInitialized(EconomicMap.emptyMap());

    /** True if the class initializer was successfully simulated as initialized. */
    final boolean simulatedInitialized;
    /** The simulated field values published in case of a successful simulation. */
    final UnmodifiableEconomicMap<AnalysisField, JavaConstant> staticFieldValues;

    static SimulateClassInitializerResult forInitialized(EconomicMap<AnalysisField, JavaConstant> staticFieldValues) {
        return new SimulateClassInitializerResult(true, staticFieldValues);
    }

    private SimulateClassInitializerResult(boolean simulatedInitialized, EconomicMap<AnalysisField, JavaConstant> staticFieldValues) {
        this.simulatedInitialized = simulatedInitialized;
        this.staticFieldValues = staticFieldValues;
    }
}
