/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

import jdk.graal.compiler.core.gen.NodeMatchRules;
import jdk.graal.compiler.core.match.MatchStatement;
import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.phases.BasePhase;

/**
 * Holds data that is pre-computed during native image generation and accessed at run time during a
 * Graal compilation.
 */
public class GraalCompilerSupport {

    public final EconomicMap<Class<?>, NodeClass<?>> nodeClasses = ImageHeapMap.create("nodeClasses");
    public final EconomicMap<Class<?>, LIRInstructionClass<?>> instructionClasses = ImageHeapMap.create("instructionClasses");
    public HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry;

    protected EconomicMap<Class<?>, BasePhase.BasePhaseStatistics> basePhaseStatistics;
    protected EconomicMap<Class<?>, LIRPhase.LIRPhaseStatistics> lirPhaseStatistics;

    protected final List<DebugDumpHandlersFactory> debugHandlersFactories = new ArrayList<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    public GraalCompilerSupport() {
        for (DebugDumpHandlersFactory c : DebugDumpHandlersFactory.LOADER) {
            debugHandlersFactories.add(c);
        }
    }

    public HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> getMatchRuleRegistry() {
        return matchRuleRegistry;
    }

    public void setMatchRuleRegistry(HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry) {
        this.matchRuleRegistry = matchRuleRegistry;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void allocatePhaseStatisticsCache() {
        GraalCompilerSupport.get().basePhaseStatistics = ImageHeapMap.create("basePhaseStatistics");
        GraalCompilerSupport.get().lirPhaseStatistics = ImageHeapMap.create("lirPhaseStatistics");
    }

    /* Invoked once for every class that is reachable in the native image. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerPhaseStatistics(DuringAnalysisAccess a, Class<?> newlyReachableClass) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        if (!Modifier.isAbstract(newlyReachableClass.getModifiers())) {
            if (BasePhase.class.isAssignableFrom(newlyReachableClass)) {
                registerStatistics(newlyReachableClass, GraalCompilerSupport.get().basePhaseStatistics, new BasePhase.BasePhaseStatistics(newlyReachableClass), access);

            } else if (LIRPhase.class.isAssignableFrom(newlyReachableClass)) {
                registerStatistics(newlyReachableClass, GraalCompilerSupport.get().lirPhaseStatistics, new LIRPhase.LIRPhaseStatistics(newlyReachableClass), access);

            }
        }
    }

    private static <S> void registerStatistics(Class<?> phaseSubClass, EconomicMap<Class<?>, S> cache, S newStatistics, DuringAnalysisAccessImpl access) {
        assert !cache.containsKey(phaseSubClass);

        cache.put(phaseSubClass, newStatistics);
        access.requireAnalysisIteration();
    }

    public static GraalCompilerSupport get() {
        return ImageSingletons.lookup(GraalCompilerSupport.class);
    }

    public EconomicMap<Class<?>, BasePhase.BasePhaseStatistics> getBasePhaseStatistics() {
        return basePhaseStatistics;
    }

    public EconomicMap<Class<?>, LIRPhase.LIRPhaseStatistics> getLirPhaseStatistics() {
        return lirPhaseStatistics;
    }

    public List<DebugDumpHandlersFactory> getDebugHandlersFactories() {
        return debugHandlersFactories;
    }
}
