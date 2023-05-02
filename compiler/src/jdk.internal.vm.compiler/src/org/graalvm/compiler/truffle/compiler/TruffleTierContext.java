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
package org.graalvm.compiler.truffle.compiler;

import java.util.Objects;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.options.OptionValues;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public final class TruffleTierContext extends HighTierContext {
    public final PartialEvaluator partialEvaluator;
    public final OptionValues options;
    public final DebugContext debug;

    public final JavaConstant compilableConstant;
    public final CompilableTruffleAST compilable;
    public final CompilationIdentifier compilationId;
    public final SpeculationLog log;

    public final TruffleCompilationTask task;

    public final StructuredGraph graph;
    public final PerformanceInformationHandler handler;

    public TruffleTierContext(PartialEvaluator partialEvaluator, OptionValues options, DebugContext debug,
                    CompilableTruffleAST compilable, ResolvedJavaMethod method,
                    CompilationIdentifier compilationId, SpeculationLog log,
                    TruffleCompilationTask task, PerformanceInformationHandler handler) {
        super(partialEvaluator.getProviders(), new PhaseSuite<>(), OptimisticOptimizations.NONE);
        Objects.requireNonNull(options);
        Objects.requireNonNull(debug);
        Objects.requireNonNull(compilable);
        Objects.requireNonNull(compilationId);
        Objects.requireNonNull(task);
        this.partialEvaluator = partialEvaluator;
        this.options = options;
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
