/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.util.Objects;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.Cancellable;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.truffle.nodes.TruffleAssumption;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public final class TruffleTierContext extends HighTierContext {
    public final PartialEvaluator partialEvaluator;
    public final OptionValues compilerOptions;
    public final DebugContext debug;

    public final JavaConstant compilableConstant;
    public final TruffleCompilable compilable;
    public final CompilationIdentifier compilationId;
    public final SpeculationLog log;

    public final TruffleCompilationTask task;

    public final StructuredGraph graph;
    public final PerformanceInformationHandler handler;

    public TruffleTierContext(PartialEvaluator partialEvaluator,
                    OptionValues compilerOptions,
                    DebugContext debug,
                    TruffleCompilable compilable, ResolvedJavaMethod method,
                    CompilationIdentifier compilationId, SpeculationLog log,
                    TruffleCompilationTask task, PerformanceInformationHandler handler) {
        super(partialEvaluator.getProviders(), new PhaseSuite<>(), OptimisticOptimizations.NONE);
        Objects.requireNonNull(debug);
        Objects.requireNonNull(compilable);
        Objects.requireNonNull(compilationId);
        Objects.requireNonNull(task);
        this.compilerOptions = compilerOptions;
        this.partialEvaluator = partialEvaluator;
        this.debug = debug;
        this.compilableConstant = compilable.asJavaConstant();
        this.compilable = compilable;
        this.compilationId = compilationId;
        this.log = log;
        this.task = task;
        this.handler = handler;
        this.graph = createInitialGraph(method);
    }

    private StructuredGraph createInitialGraph(ResolvedJavaMethod method) {
        compilable.prepareForCompilation();

        // @formatter:off
        StructuredGraph.Builder builder = new StructuredGraph.Builder(this.debug.getOptions(), this.debug, StructuredGraph.AllowAssumptions.YES).
                name(this.compilable.getName()).
                method(method).
                speculationLog(this.log).
                compilationId(this.compilationId).
                trackNodeSourcePosition(partialEvaluator.graphBuilderConfigForParsing.trackNodeSourcePosition()).
                cancellable(new CancellableTask(this.task));
        // @formatter:on
        builder = partialEvaluator.customizeStructuredGraphBuilder(builder);
        StructuredGraph g = builder.build();
        g.getAssumptions().record(new TruffleAssumption(getValidRootAssumption(partialEvaluator.getProviders())));
        g.getAssumptions().record(new TruffleAssumption(getNodeRewritingAssumption(partialEvaluator.getProviders())));
        return g;
    }

    public PartialEvaluator getPartialEvaluator() {
        return partialEvaluator;
    }

    public TruffleCompilerConfiguration config() {
        return partialEvaluator.config;
    }

    public KnownTruffleTypes types() {
        return config().types();
    }

    public TruffleCompilerRuntime runtime() {
        return config().runtime();
    }

    public JavaConstant getNodeRewritingAssumption(Providers providers) {
        JavaConstant constant = readCompilableField(providers, types().OptimizedCallTarget_nodeRewritingAssumption);
        if (constant.isNull()) {
            throw GraalError.shouldNotReachHere("OptimizedCallTarget.nodeRewritingAssumption must not be null.");
        }
        return constant;
    }

    public JavaConstant getValidRootAssumption(Providers providers) {
        JavaConstant constant = readCompilableField(providers, types().OptimizedCallTarget_validRootAssumption);
        if (constant.isNull()) {
            throw GraalError.shouldNotReachHere("OptimizedCallTarget_validRootAssumption must not be null.");
        }
        return constant;
    }

    private JavaConstant readCompilableField(Providers providers, ResolvedJavaField field) {
        return providers.getConstantReflection().readFieldValue(field, compilableConstant);
    }

    public boolean isFirstTier() {
        return task.isFirstTier();
    }

    public static SpeculationLog getSpeculationLog(TruffleCompilerImpl.TruffleCompilationWrapper wrapper) {
        SpeculationLog speculationLog = wrapper.compilable.getCompilationSpeculationLog();
        if (speculationLog != null) {
            speculationLog.collectFailedSpeculations();
        }
        return speculationLog;
    }

    /**
     * Wrapper around a {@link TruffleCompilationTask} which also implements {@link Cancellable} to
     * allow co-operative canceling of truffle compilations.
     */
    private static final class CancellableTask implements Cancellable {
        private final TruffleCompilationTask delegate;

        CancellableTask(TruffleCompilationTask delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

    }

}
