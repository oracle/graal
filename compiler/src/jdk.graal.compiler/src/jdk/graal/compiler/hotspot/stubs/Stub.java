/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.stubs;

import static jdk.graal.compiler.core.GraalCompiler.emitFrontEnd;
import static jdk.graal.compiler.core.common.GraalOptions.RegisterPressure;
import static jdk.graal.compiler.debug.DebugOptions.DebugStubsAndSnippets;
import static jdk.graal.compiler.util.CollectionsUtil.allMatch;

import java.util.ListIterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.CompilationPrinter;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugContext.Description;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.hotspot.HotSpotCompiledCodeBuilder;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotHostBackend;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.nodes.StubStartNode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import jdk.graal.compiler.lir.profiling.MoveProfilingPhase;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

//JaCoCo Exclude

/**
 * Base class for implementing some low level code providing the out-of-line slow path for a snippet
 * and/or a callee saved call to a HotSpot C/C++ runtime function or even another compiled Java
 * method.
 */
public abstract class Stub {

    /**
     * The linkage information for a call to this stub from compiled code.
     */
    protected final HotSpotForeignCallLinkage linkage;

    /**
     * The code installed for the stub.
     */
    protected InstalledCode code;

    /**
     * The registers destroyed by this stub (from the caller's perspective).
     */
    private EconomicSet<Register> destroyedCallerRegisters;

    private static boolean checkRegisterSetEquivalency(EconomicSet<Register> a, EconomicSet<Register> b) {
        if (a == b) {
            return true;
        }
        if (a.size() != b.size()) {
            return false;
        }
        return allMatch(a, e -> b.contains(e));
    }

    public void initDestroyedCallerRegisters(EconomicSet<Register> registers) {
        assert registers != null;
        assert destroyedCallerRegisters == null || checkRegisterSetEquivalency(registers, destroyedCallerRegisters) : "cannot redefine";
        destroyedCallerRegisters = registers;
    }

    /**
     * Gets the registers destroyed by this stub from a caller's perspective. These are the
     * temporaries of this stub and must thus be caller saved by a caller of this stub.
     */
    public EconomicSet<Register> getDestroyedCallerRegisters() {
        assert destroyedCallerRegisters != null : "not yet initialized";
        return destroyedCallerRegisters;
    }

    protected final OptionValues options;
    protected final HotSpotProviders providers;

    /**
     * Creates a new stub.
     *
     * @param linkage linkage details for a call to the stub
     */
    public Stub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        this.linkage = linkage;
        // The RegisterPressure flag can be ignored by a compilation that runs out of registers, so
        // the stub compilation must ignore the flag so that all allocatable registers are saved.
        this.options = new OptionValues(options, GraalOptions.TraceInlining, GraalOptions.TraceInliningForStubsAndSnippets.getValue(options), RegisterPressure, null,
                        DebugOptions.OptimizationLog, null);
        this.providers = providers;
    }

    /**
     * Gets the linkage for a call to this stub from compiled code.
     */
    public HotSpotForeignCallLinkage getLinkage() {
        return linkage;
    }

    public RegisterConfig getRegisterConfig() {
        return null;
    }

    /**
     * Gets the graph that from which the code for this stub will be compiled.
     *
     * @param compilationId unique compilation id for the stub
     */
    protected abstract StructuredGraph getGraph(DebugContext debug, CompilationIdentifier compilationId);

    @Override
    public String toString() {
        return "Stub<" + linkage.getDescriptor().getSignature() + ">";
    }

    /**
     * Gets the method the stub's code will be associated with once installed. This may be null.
     */
    protected abstract ResolvedJavaMethod getInstalledCodeOwner();

    /**
     * Gets a context object for the debug scope created when producing the code for this stub.
     */
    protected abstract Object debugScopeContext();

    private static final AtomicInteger nextStubId = new AtomicInteger();

    private DebugContext openDebugContext(DebugContext outer) {
        if (DebugStubsAndSnippets.getValue(options)) {
            Description description = new Description(linkage, "Stub_" + nextStubId.incrementAndGet());
            GraalDebugHandlersFactory factory = new GraalDebugHandlersFactory(providers.getSnippetReflection());
            return new Builder(options, factory).globalMetrics(outer.getGlobalMetrics()).description(description).build();
        }
        return DebugContext.disabled(options);
    }

    /**
     * Gets the code for this stub, compiling it first if necessary.
     */
    @SuppressWarnings("try")
    public synchronized InstalledCode getCode(final Backend backend) {
        if (code == null) {
            try (DebugContext debug = openDebugContext(DebugContext.forCurrentThread())) {
                try (DebugContext.Scope d = debug.scope("CompilingStub", providers.getCodeCache(), debugScopeContext())) {
                    CompilationIdentifier compilationId = getStubCompilationId();
                    final StructuredGraph graph = getGraph(debug, compilationId);
                    CompilationPrinter printer = CompilationPrinter.begin(debug.getOptions(), compilationId, linkage.getDescriptor().getSignature(), -1);
                    CodeCacheProvider codeCache = providers.getCodeCache();
                    CompilationResult compResult = buildCompilationResult(debug, backend, graph, compilationId);
                    try (DebugContext.Scope s = debug.scope("CodeInstall", compResult);
                                    DebugContext.Activation a = debug.activate()) {
                        assert destroyedCallerRegisters != null;
                        HotSpotCompiledCode compiledCode = HotSpotCompiledCodeBuilder.createCompiledCode(codeCache, null, null, compResult, options);
                        code = codeCache.installCode(null, compiledCode, null, null, false);
                    } catch (Throwable e) {
                        throw debug.handle(e);
                    }
                    printer.finish(compResult, code);
                } catch (Throwable e) {
                    throw debug.handle(e);
                }
                assert code != null : "error installing stub " + this;
            }
        }

        return code;
    }

    @SuppressWarnings("try")
    private CompilationResult buildCompilationResult(DebugContext debug, final Backend backend, StructuredGraph graph, CompilationIdentifier compilationId) {
        CompilationResult compResult = new CompilationResult(compilationId, toString());

        // Stubs cannot be recompiled so they cannot be compiled with assumptions
        assert graph.getAssumptions() == null;

        if (!(graph.start() instanceof StubStartNode)) {
            StubStartNode newStart = graph.add(new StubStartNode(Stub.this));
            newStart.setStateAfter(graph.start().stateAfter());
            graph.replaceFixed(graph.start(), newStart);
        }

        try (DebugContext.Scope s0 = debug.scope("StubCompilation", graph, providers.getCodeCache())) {
            Suites suites = createSuites();
            emitFrontEnd(providers, backend, graph, providers.getSuites().getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL, DefaultProfilingInfo.get(TriState.UNKNOWN), suites);
            LIRSuites lirSuites = createLIRSuites();
            backend.emitBackEnd(graph, Stub.this, getInstalledCodeOwner(), compResult, CompilationResultBuilderFactory.Default, null, getRegisterConfig(), lirSuites);
            assert checkStubInvariants(compResult);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        return compResult;
    }

    public CompilationIdentifier getStubCompilationId() {
        return new StubCompilationIdentifier(this);
    }

    /**
     * Checks the conditions a compilation must satisfy to be installed as a RuntimeStub.
     */
    private boolean checkStubInvariants(CompilationResult compResult) {
        assert compResult.getExceptionHandlers().isEmpty() : this;

        // Stubs cannot be recompiled so they cannot be compiled with
        // assumptions and there is no point in recording evol_method dependencies
        assert compResult.getAssumptions() == null : "stubs should not use assumptions: " + this;

        for (DataPatch data : compResult.getDataPatches()) {
            if (data.reference instanceof ConstantReference) {
                ConstantReference ref = (ConstantReference) data.reference;
                if (ref.getConstant() instanceof HotSpotMetaspaceConstant) {
                    HotSpotMetaspaceConstant c = (HotSpotMetaspaceConstant) ref.getConstant();
                    if (c.asResolvedJavaType() != null && c.asResolvedJavaType().getName().equals("[I")) {
                        // special handling for NewArrayStub
                        // embedding the type '[I' is safe, since it is never unloaded
                        continue;
                    }
                }
            }

            checkSafeDataReference(data);
        }
        for (Infopoint infopoint : compResult.getInfopoints()) {
            assert infopoint instanceof Call : this + " cannot have non-call infopoint: " + infopoint;
            Call call = (Call) infopoint;
            assert call.target instanceof HotSpotForeignCallLinkage : this + " cannot have non runtime call: " + call.target;
            HotSpotForeignCallLinkage callLinkage = (HotSpotForeignCallLinkage) call.target;
            assert !callLinkage.isCompiledStub() || callLinkage.getDescriptor().equals(HotSpotHostBackend.DEOPT_BLOB_UNCOMMON_TRAP) : this + " cannot call compiled stub " + callLinkage;
        }
        return true;
    }

    protected void checkSafeDataReference(DataPatch data) {
        assert !(data.reference instanceof ConstantReference) : this + " cannot have embedded object or metadata constant: " + data.reference;
    }

    private static class EmptyHighTier extends BasePhase<HighTierContext> {
        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, HighTierContext context) {
        }

        @Override
        public void updateGraphState(GraphState graphState) {
            super.updateGraphState(graphState);
            if (graphState.isBeforeStage(StageFlag.HIGH_TIER_LOWERING)) {
                graphState.setAfterStage(StageFlag.HIGH_TIER_LOWERING);
            }
        }

    }

    protected Suites createSuites() {
        Suites defaultSuites = providers.getSuites().getDefaultSuites(options, providers.getLowerer().getTarget().arch).copy();

        PhaseSuite<HighTierContext> emptyHighTier = new PhaseSuite<>();
        emptyHighTier.appendPhase(new DisableOverflownCountedLoopsPhase());
        emptyHighTier.appendPhase(new EmptyHighTier());

        defaultSuites.getMidTier().removeSubTypePhases(Speculative.class);
        defaultSuites.getLowTier().removeSubTypePhases(Speculative.class);

        return new Suites(emptyHighTier, defaultSuites.getMidTier(), defaultSuites.getLowTier());
    }

    protected LIRSuites createLIRSuites() {
        LIRSuites lirSuites = new LIRSuites(providers.getSuites().getDefaultLIRSuites(options));
        ListIterator<LIRPhase<PostAllocationOptimizationContext>> moveProfiling = lirSuites.getPostAllocationOptimizationStage().findPhase(MoveProfilingPhase.class);
        if (moveProfiling != null) {
            moveProfiling.remove();
        }
        return lirSuites;
    }
}
