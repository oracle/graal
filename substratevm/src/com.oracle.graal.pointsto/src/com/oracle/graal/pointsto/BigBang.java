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
package com.oracle.graal.pointsto;

import java.io.PrintWriter;
import java.util.List;
import java.util.function.Function;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.common.meta.MultiMethod;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Central static analysis interface that groups together the functionality of reachability analysis
 * and heap scanning and adds utility methods and lifecycle hooks that should be used to query and
 * change the state of the analysis.
 *
 * In long term, all mutable accesses that change the state of the analysis should go through this
 * interface.
 *
 * @see PointsToAnalysis
 */
public interface BigBang extends ReachabilityAnalysis {
    HostVM getHostVM();

    UnsupportedFeatures getUnsupportedFeatures();

    /**
     * Checks if all user defined limitations such as the number of types are satisfied.
     */
    void checkUserLimitations();

    OptionValues getOptions();

    default HostedProviders getProviders(AnalysisMethod method) {
        return getProviders(method.getMultiMethodKey());
    }

    HostedProviders getProviders(MultiMethod.MultiMethodKey key);

    List<DebugHandlersFactory> getDebugHandlerFactories();

    /**
     * Prints more detailed information about all analysis timers.
     */
    void printTimerStatistics(PrintWriter out);

    ConstantReflectionProvider getConstantReflectionProvider();

    SnippetReflectionProvider getSnippetReflectionProvider();

    WordTypes getWordTypes();

    DebugContext getDebug();

    void runAnalysis(DebugContext debug, Function<AnalysisUniverse, Boolean> duringAnalysisAction) throws InterruptedException;

    boolean trackPrimitiveValues();

    /** You can blacklist certain callees here. */
    @SuppressWarnings("unused")
    default boolean isCallAllowed(PointsToAnalysis bb, AnalysisMethod caller, AnalysisMethod target, BytecodePosition srcPosition) {
        return true;
    }

    /**
     * Callback for when a field is marked as read, written, or unsafe accessed. See
     * {@link AnalysisField#isAccessed()} for field accessibility definition.
     */
    @SuppressWarnings("unused")
    default void onFieldAccessed(AnalysisField field) {
    }

    @SuppressWarnings("unused")
    default void injectFieldTypes(AnalysisField aField, List<AnalysisType> customTypes, boolean canBeNull) {
    }

    @SuppressWarnings("unused")
    default void onTypeInstantiated(AnalysisType type) {
    }

    @SuppressWarnings("unused")
    default void onTypeReachable(AnalysisType type) {
    }

    void postTask(CompletionExecutor.DebugContextRunnable task);

    boolean executorIsStarted();

    void initializeMetaData(AnalysisType type);

    /**
     * Callback executed after the analysis finished. The cleanupAfterAnalysis is executed after the
     * universe builder, which can be too late for some tasks.
     */
    default void afterAnalysis() {

    }

    @SuppressWarnings("unused")
    default AnalysisMethod fallbackResolveConcreteMethod(AnalysisType resolvingType, AnalysisMethod method) {
        return null;
    }

    @SuppressWarnings("unused")
    default void registerTypeForBaseImage(Class<?> cls) {

    }
}
