/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.api.code.CodeUtil.*;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Assumptions.Assumption;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.truffle.nodes.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Implementation of the Truffle compiler using Graal.
 */
public class TruffleCompilerImpl implements TruffleCompiler {

    private final Providers providers;
    private final Suites suites;
    private final PartialEvaluator partialEvaluator;
    private final Backend backend;
    private final ResolvedJavaType[] skippedExceptionTypes;
    private final HotSpotGraalRuntime graalRuntime;
    private final TruffleCache truffleCache;

    private static final Class[] SKIPPED_EXCEPTION_CLASSES = new Class[]{SlowPathException.class, UnexpectedResultException.class, ArithmeticException.class};

    public static final OptimisticOptimizations Optimizations = OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.UseExceptionProbability,
                    OptimisticOptimizations.Optimization.RemoveNeverExecutedCode, OptimisticOptimizations.Optimization.UseTypeCheckedInlining, OptimisticOptimizations.Optimization.UseTypeCheckHints);

    public TruffleCompilerImpl() {
        Replacements truffleReplacements = ((GraalTruffleRuntime) Truffle.getRuntime()).getReplacements();
        this.providers = GraalCompiler.getGraalProviders().copyWith(truffleReplacements);
        this.suites = Graal.getRequiredCapability(SuitesProvider.class).createSuites();
        this.backend = Graal.getRequiredCapability(Backend.class);
        this.graalRuntime = HotSpotGraalRuntime.graalRuntime();
        this.skippedExceptionTypes = getSkippedExceptionTypes(providers.getMetaAccess());

        final GraphBuilderConfiguration config = GraphBuilderConfiguration.getEagerDefault();
        config.setSkippedExceptionTypes(skippedExceptionTypes);
        this.truffleCache = new TruffleCache(providers, config, TruffleCompilerImpl.Optimizations);

        this.partialEvaluator = new PartialEvaluator(providers, truffleCache);

        if (Debug.isEnabled()) {
            DebugEnvironment.initialize(System.out);
        }
    }

    static ResolvedJavaType[] getSkippedExceptionTypes(MetaAccessProvider metaAccess) {
        ResolvedJavaType[] skippedExceptionTypes = new ResolvedJavaType[SKIPPED_EXCEPTION_CLASSES.length];
        for (int i = 0; i < SKIPPED_EXCEPTION_CLASSES.length; i++) {
            skippedExceptionTypes[i] = metaAccess.lookupJavaType(SKIPPED_EXCEPTION_CLASSES[i]);
        }
        return skippedExceptionTypes;
    }

    public InstalledCode compile(final OptimizedCallTarget compilable) {
        Object[] debug = new Object[]{new DebugDumpScope("Truffle: " + compilable)};
        return Debug.scope("Truffle", debug, new Callable<InstalledCode>() {

            @Override
            public InstalledCode call() throws Exception {
                return compileMethodImpl(compilable);
            }
        });
    }

    public static final DebugTimer PartialEvaluationTime = Debug.timer("PartialEvaluationTime");
    public static final DebugTimer CompilationTime = Debug.timer("CompilationTime");
    public static final DebugTimer CodeInstallationTime = Debug.timer("CodeInstallation");

    private InstalledCode compileMethodImpl(final OptimizedCallTarget compilable) {
        final StructuredGraph graph;
        final GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault();
        config.setSkippedExceptionTypes(skippedExceptionTypes);
        graalRuntime.evictDeoptedGraphs();

        compilable.timeCompilationStarted = System.nanoTime();
        Assumptions assumptions = new Assumptions(true);
        try (TimerCloseable a = PartialEvaluationTime.start()) {
            graph = partialEvaluator.createGraph(compilable, assumptions);
        }
        compilable.timePartialEvaluationFinished = System.nanoTime();
        compilable.nodeCountPartialEval = graph.getNodeCount();
        InstalledCode compiledMethod = compileMethodHelper(graph, config, compilable, assumptions);
        compilable.timeCompilationFinished = System.nanoTime();
        compilable.nodeCountLowered = graph.getNodeCount();
        return compiledMethod;
    }

    public InstalledCode compileMethodHelper(final StructuredGraph graph, final GraphBuilderConfiguration config, final Assumptions assumptions) {
        return compileMethodHelper(graph, config, null, assumptions);
    }

    public InstalledCode compileMethodHelper(final StructuredGraph graph, final GraphBuilderConfiguration config, final OptimizedCallTarget compilable, final Assumptions assumptions) {
        final PhasePlan plan = createPhasePlan(config);

        Debug.scope("TruffleFinal", graph, new Runnable() {

            @Override
            public void run() {
                Debug.dump(graph, "After TruffleTier");
            }
        });

        final CompilationResult result = Debug.scope("TruffleGraal", new Callable<CompilationResult>() {

            @Override
            public CompilationResult call() {
                try (TimerCloseable a = CompilationTime.start()) {
                    CodeCacheProvider codeCache = providers.getCodeCache();
                    CallingConvention cc = getCallingConvention(codeCache, Type.JavaCallee, graph.method(), false);
                    return GraalCompiler.compileGraph(graph, cc, graph.method(), providers, backend, codeCache.getTarget(), null, plan, OptimisticOptimizations.ALL, new SpeculationLog(), suites,
                                    new CompilationResult());
                }
            }
        });

        List<AssumptionValidAssumption> validAssumptions = new ArrayList<>();
        Assumptions newAssumptions = new Assumptions(true);
        if (assumptions != null) {
            for (Assumption assumption : assumptions.getAssumptions()) {
                processAssumption(newAssumptions, assumption, validAssumptions);
            }
        }

        if (result.getAssumptions() != null) {
            for (Assumption assumption : result.getAssumptions().getAssumptions()) {
                processAssumption(newAssumptions, assumption, validAssumptions);
            }
        }

        result.setAssumptions(newAssumptions);

        InstalledCode compiledMethod = Debug.scope("CodeInstall", new Object[]{graph.method()}, new Callable<InstalledCode>() {

            @Override
            public InstalledCode call() throws Exception {
                try (TimerCloseable a = CodeInstallationTime.start()) {
                    InstalledCode installedCode = providers.getCodeCache().addMethod(graph.method(), result);
                    if (installedCode != null) {
                        Debug.dump(new Object[]{result, installedCode}, "After code installation");
                    }
                    return installedCode;
                }
            }
        });

        for (AssumptionValidAssumption a : validAssumptions) {
            a.getAssumption().registerInstalledCode(compiledMethod);
        }

        if (Debug.isLogEnabled()) {
            Debug.log(providers.getCodeCache().disassemble(result, compiledMethod));
        }
        if (compilable != null) {
            compilable.codeSize = result.getTargetCodeSize();
        }
        return compiledMethod;
    }

    private PhasePlan createPhasePlan(final GraphBuilderConfiguration config) {
        final PhasePlan phasePlan = new PhasePlan();
        GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(providers.getMetaAccess(), providers.getForeignCalls(), config, TruffleCompilerImpl.Optimizations);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
        return phasePlan;
    }

    public void processAssumption(Assumptions newAssumptions, Assumption assumption, List<AssumptionValidAssumption> manual) {
        if (assumption != null) {
            if (assumption instanceof AssumptionValidAssumption) {
                AssumptionValidAssumption assumptionValidAssumption = (AssumptionValidAssumption) assumption;
                manual.add(assumptionValidAssumption);
            } else {
                newAssumptions.record(assumption);
            }
        }
    }
}
