/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.AbstractAnalysisEngine;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.typestate.TypeState;
import org.graalvm.compiler.options.OptionValues;

import java.lang.reflect.Executable;
import java.util.concurrent.ForkJoinPool;

public class ReachabilityAnalysis extends AbstractAnalysisEngine {

    public ReachabilityAnalysis(OptionValues options, AnalysisUniverse universe, HostedProviders providers, HostVM hostVM, ForkJoinPool executorService, Runnable heartbeatCallback,
                    UnsupportedFeatures unsupportedFeatures) {
        super(options, universe, providers, hostVM, executorService, heartbeatCallback, unsupportedFeatures);
    }

    @Override
    public void checkUserLimitations() {

    }

    @Override
    public AnalysisType addRootClass(Class<?> clazz, boolean addFields, boolean addArrayClass) {
        return null;
    }

    @Override
    public AnalysisType addRootField(Class<?> clazz, String fieldName) {
        return null;
    }

    @Override
    public AnalysisMethod addRootMethod(AnalysisMethod aMethod) {
        return null;
    }

    @Override
    public AnalysisMethod addRootMethod(Executable method) {
        return null;
    }

    @Override
    public AnalysisMethod addRootMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        return null;
    }

    @Override
    public boolean finish() throws InterruptedException {
        return false;
    }

    @Override
    public void cleanupAfterAnalysis() {

    }

    @Override
    public void forceUnsafeUpdate(AnalysisField field) {

    }

    @Override
    public void registerAsJNIAccessed(AnalysisField field, boolean writable) {

    }

    @Override
    public TypeState getAllSynchronizedTypeState() {
        return null;
    }
}
