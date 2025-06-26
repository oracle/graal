/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.microbenchmarks.lir;

import static jdk.graal.compiler.microbenchmarks.graal.util.GraalUtil.getGraph;
import static jdk.graal.compiler.microbenchmarks.graal.util.GraalUtil.getMethodFromMethodSpec;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.GraalCompiler.Request;
import jdk.graal.compiler.core.LIRGenerationPhase;
import jdk.graal.compiler.core.LIRGenerationPhase.LIRGenerationContext;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.alloc.LinearScanOrder;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.CodeEmissionOrder;
import jdk.graal.compiler.core.gen.LIRCompilerBackend;
import jdk.graal.compiler.core.gen.LIRGenerationProvider;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.phases.AllocationPhase.AllocationContext;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import jdk.graal.compiler.microbenchmarks.graal.util.GraalState;
import jdk.graal.compiler.microbenchmarks.graal.util.GraalUtil;
import jdk.graal.compiler.microbenchmarks.graal.util.MethodSpec;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.tiers.TargetProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * State providing a new copy of a graph for each invocation of a benchmark. Subclasses of this
 * class are annotated with {@link MethodSpec} to specify the Java method that will be parsed to
 * obtain the original graph.
 */
@State(Scope.Thread)
public abstract class GraalCompilerState {

    /**
     * Original graph from which the per-benchmark invocation {@link #graph} is cloned.
     */
    private StructuredGraph originalGraph;

    /**
     * The graph processed by the benchmark.
     */
    private final OptionValues options;
    private final DebugContext debug;
    private StructuredGraph graph;
    private final Backend backend;
    private final Providers providers;

    /**
     * We only allow inner classes to subclass this to ensure that the {@link Setup} methods are
     * executed in the right order.
     */
    @SuppressWarnings("try")
    protected GraalCompilerState() {
        this.options = Graal.getRequiredCapability(OptionValues.class);
        this.backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        this.providers = backend.getProviders();
        this.debug = new Builder(options).build();
    }

    protected boolean useProfilingInfo() {
        return false;
    }

    @SuppressWarnings("try")
    protected void initializeMethod() {
        GraalState graal = new GraalState();
        ResolvedJavaMethod method = graal.metaAccess.lookupJavaMethod(getMethod());
        StructuredGraph structuredGraph = null;
        try (DebugContext.Scope s = debug.scope("GraphState", method)) {
            structuredGraph = preprocessOriginal(getGraph(graal, method, useProfilingInfo()));
        } catch (Throwable t) {
            debug.handle(t);
        }
        this.originalGraph = structuredGraph;
    }

    protected Method getMethod() {
        Class<?> c = getClass();
        if (isMethodSpecAnnotationPresent(c)) {
            return getMethodFromMethodSpec(c);
        }
        return findParamField(this);
    }

    protected boolean isMethodSpecAnnotationPresent(Class<?> startClass) {
        Class<?> c = startClass;
        while (c != null) {
            if (c.isAnnotationPresent(MethodSpec.class)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    /**
     * Declares {@link GraalCompilerState#getMethodFromString(String) method description field}. The
     * field must be a {@link String} and have a {@link Param} annotation.
     */
    @Inherited
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MethodDescString {
    }

    private static Method findParamField(Object obj) {
        Class<?> c = obj.getClass();
        Class<? extends Annotation> annotationClass = MethodDescString.class;
        try {
            for (Field f : c.getFields()) {
                if (f.isAnnotationPresent(annotationClass)) {
                    // these checks could be done by an annotation processor
                    if (!f.getType().equals(String.class)) {
                        throw new RuntimeException("Found a field annotated with " + annotationClass.getSimpleName() + " in " + c + " which is not a " + String.class.getSimpleName());
                    }
                    if (!f.isAnnotationPresent(Param.class)) {
                        throw new RuntimeException("Found a field annotated with " + annotationClass.getSimpleName() + " in " + c + " which is not annotated with " + Param.class.getSimpleName());
                    }
                    String methodName;
                    methodName = (String) f.get(obj);
                    assert methodName != null;
                    return getMethodFromString(methodName);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("Could not find class annotated with " + annotationClass.getSimpleName() + " in hierarchy of " + c);
    }

    /**
     * Gets a {@link Method} from a method description string. The format is as follows:
     *
     * <pre>
     * ClassName#MethodName
     * ClassName#MethodName(ClassName, ClassName, ...)
     * </pre>
     *
     * <code>CodeName</code> is passed to {@link Class#forName(String)}. <br>
     * <b>Examples:</b>
     *
     * <pre>
     * java.lang.String#equals
     * java.lang.String#equals(java.lang.Object)
     * </pre>
     */
    protected static Method getMethodFromString(String methodDesc) {
        try {
            String[] s0 = methodDesc.split("#", 2);
            if (s0.length != 2) {
                throw new RuntimeException("Missing method description? " + methodDesc);
            }
            String className = s0[0];
            Class<?> clazz = Class.forName(className);
            String[] s1 = s0[1].split("\\(", 2);
            String name = s1[0];
            Class<?>[] parameters = null;
            if (s1.length > 1) {
                String parametersPart = s1[1];
                if (parametersPart.charAt(parametersPart.length() - 1) != ')') {
                    throw new RuntimeException("Missing closing ')'? " + methodDesc);
                }
                String[] s2 = parametersPart.substring(0, parametersPart.length() - 1).split(",");
                parameters = new Class<?>[s2.length];
                for (int i = 0; i < s2.length; i++) {
                    parameters[i] = Class.forName(s2[i]);
                }
            }
            return GraalUtil.getMethod(clazz, name, parameters);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected StructuredGraph preprocessOriginal(StructuredGraph structuredGraph) {
        return structuredGraph;
    }

    protected OptionValues getOptions() {
        return options;
    }

    protected Suites createSuites(OptionValues opts) {
        return backend.getSuites().getDefaultSuites(opts, backend.getTarget().arch).copy();
    }

    protected LIRSuites createLIRSuites(OptionValues opts) {
        return backend.getSuites().getDefaultLIRSuites(opts).copy();
    }

    protected Backend getBackend() {
        return backend;
    }

    protected Providers getProviders() {
        return providers;
    }

    protected SnippetReflectionProvider getSnippetReflection() {
        return Graal.getRequiredCapability(SnippetReflectionProvider.class);
    }

    protected TargetDescription getTarget() {
        return getTargetProvider().getTarget();
    }

    protected TargetProvider getTargetProvider() {
        return getBackend();
    }

    protected CodeCacheProvider getCodeCache() {
        return getProviders().getCodeCache();
    }

    protected ConstantReflectionProvider getConstantReflection() {
        return getProviders().getConstantReflection();
    }

    protected MetaAccessProvider getMetaAccess() {
        return getProviders().getMetaAccess();
    }

    protected LoweringProvider getLowerer() {
        return getProviders().getLowerer();
    }

    protected PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        // defensive copying
        return backend.getSuites().getDefaultGraphBuilderSuite().copy();
    }

    protected LIRSuites getLIRSuites() {
        return request.lirSuites();
    }

    private Request<CompilationResult> request;
    private LIRGenerationResult lirGenRes;
    private LIRGeneratorTool lirGenTool;
    private NodeLIRBuilderTool nodeLirGen;
    private RegisterConfig registerConfig;
    private ScheduleResult schedule;
    private CodeEmissionOrder<?> blockOrder;
    private int[] linearScanOrder;

    /**
     * Copies the {@link #originalGraph original graph} and prepares the {@link #request}.
     *
     * The {@link Suites} can be changed by overriding {@link #createSuites}. {@link LIRSuites} can
     * be changed by overriding {@link #createLIRSuites}.
     */
    protected final void prepareRequest() {
        assert originalGraph != null : "call initialzeMethod first";
        CompilationIdentifier compilationId = backend.getCompilationIdentifier(originalGraph.method());
        graph = originalGraph.copyWithIdentifier(compilationId, originalGraph.getDebug());
        assert !graph.isFrozen();
        ResolvedJavaMethod installedCodeOwner = graph.method();
        request = new Request<>(graph, installedCodeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL,
                        graph.getProfilingInfo(), createSuites(getOptions()), createLIRSuites(getOptions()), new CompilationResult(graph.compilationId()), CompilationResultBuilderFactory.Default,
                        null, null, true);
    }

    /**
     * Executes the high-level (FrontEnd) part of the compiler.
     */
    protected final void emitFrontEnd() {
        GraalCompiler.emitFrontEnd(request.providers(), request.backend(), request.graph(), request.graphBuilderSuite(), request.optimisticOpts(), request.profilingInfo(), request.suites());
        request.graph().freeze();
    }

    /**
     * Executes the low-level (BackEnd) part of the compiler.
     */
    protected final void emitBackEnd() {
        emitLIR();
        emitCode();
    }

    /**
     * Generates {@link LIR} and executes the {@link LIR} pipeline.
     */
    protected final void emitLIR() {
        generateLIR();
        emitLowLevel();
    }

    /**
     * Generates the initial {@link LIR}.
     */
    protected final void generateLIR() {
        preLIRGeneration();
        lirGeneration();
    }

    /**
     * Sets up {@link LIR} generation.
     */
    protected final void preLIRGeneration() {
        assert request.graph().isFrozen() : "Graph not frozen.";
        Object stub = null;
        schedule = request.graph().getLastSchedule();
        ControlFlowGraph cfg = deepCopy(schedule.getCFG());
        HIRBlock[] blocks = cfg.getBlocks();
        HIRBlock startBlock = cfg.getStartBlock();
        assert startBlock != null;
        assert startBlock.getPredecessorCount() == 0;

        blockOrder = request.backend().newBlockOrder(blocks.length, startBlock);
        linearScanOrder = LinearScanOrder.computeLinearScanOrder(blocks.length, startBlock);

        LIR lir = new LIR(cfg, linearScanOrder, getGraphOptions(), getGraphDebug());
        LIRGenerationProvider lirBackend = (LIRGenerationProvider) request.backend();
        RegisterAllocationConfig registerAllocationConfig = request.backend().newRegisterAllocationConfig(registerConfig, null, stub);
        lirGenRes = lirBackend.newLIRGenerationResult(graph.compilationId(), lir, registerAllocationConfig, request.graph(), stub);
        lirGenTool = lirBackend.newLIRGenerator(lirGenRes);
        nodeLirGen = lirBackend.newNodeLIRBuilder(request.graph(), lirGenTool);
    }

    protected OptionValues getGraphOptions() {
        return graph.getOptions();
    }

    protected DebugContext getGraphDebug() {
        return graph.getDebug();
    }

    private static ControlFlowGraph deepCopy(ControlFlowGraph cfg) {
        return ControlFlowGraph.newBuilder(cfg.graph).modifiableBlocks(true).connectBlocks(true).computeFrequency(true).computeLoops(true).computeDominators(true).computePostdominators(
                        true).build();
    }

    /**
     * Executes the {@link LIRGenerationPhase}.
     */
    protected final void lirGeneration() {
        LIRGenerationContext context = new LIRGenerationContext(lirGenTool, nodeLirGen, request.graph(), schedule);
        new LIRGenerationPhase().apply(request.backend().getTarget(), lirGenRes, context);
    }

    /**
     * Executes the low-level compiler stages.
     */
    protected final void emitLowLevel() {
        preAllocationStage();
        allocationStage();
        postAllocationStage();
    }

    /**
     * Executes a {@link LIRPhase} within a given {@code context}.
     */
    protected <C> void applyLIRPhase(LIRPhase<C> phase, C context) {
        phase.apply(request.backend().getTarget(), lirGenRes, context);
    }

    /**
     * Executes the {@link PreAllocationStage}.
     *
     * {@link LIRPhase phases} can be changed by overriding {@link #createLIRSuites}.
     */
    protected final void preAllocationStage() {
        applyLIRPhase(getLIRSuites().getPreAllocationOptimizationStage(), createPreAllocationOptimizationContext());
    }

    protected PreAllocationOptimizationContext createPreAllocationOptimizationContext() {
        return new PreAllocationOptimizationContext(lirGenTool);
    }

    /**
     * Executes the {@link AllocationStage}.
     *
     * {@link LIRPhase phases} can be changed by overriding {@link #createLIRSuites}.
     */
    protected final void allocationStage() {
        applyLIRPhase(getLIRSuites().getAllocationStage(), createAllocationContext());
    }

    protected AllocationContext createAllocationContext() {
        return new AllocationContext(lirGenTool.getSpillMoveFactory(), lirGenRes.getRegisterAllocationConfig());
    }

    /**
     * Executes the {@link PostAllocationStage}.
     *
     * {@link LIRPhase phases} can be changed by overriding {@link #createLIRSuites}.
     */
    protected final void postAllocationStage() {
        applyLIRPhase(getLIRSuites().getPostAllocationOptimizationStage(), createPostAllocationOptimizationContext());
    }

    protected PostAllocationOptimizationContext createPostAllocationOptimizationContext() {
        return new PostAllocationOptimizationContext(lirGenTool, blockOrder);
    }

    /**
     * Emits the machine code.
     */
    protected final void emitCode() {
        int bytecodeSize = request.graph().method() == null ? 0 : request.graph().getBytecodeSize();
        SpeculationLog speculationLog = null;
        request.compilationResult().setHasUnsafeAccess(request.graph().hasUnsafeAccess());
        LIRCompilerBackend.emitCode(request.backend(), request.graph().getAssumptions(), request.graph().method(), request.graph().getMethods(), speculationLog,
                        bytecodeSize, lirGenRes, request.compilationResult(),
                        request.installedCodeOwner(), request.factory(), request.entryPointDecorator());
    }

    protected StructuredGraph graph() {
        return graph;
    }

    protected LIR getLIR() {
        return lirGenRes.getLIR();
    }

    public abstract static class Compile extends GraalCompilerState {

        @Setup(Level.Trial)
        public void init() {
            initializeMethod();
        }

        @Setup(Level.Invocation)
        public void setup() {
            prepareRequest();
        }

        public CompilationResult compile() {
            emitFrontEnd();
            emitBackEnd();
            return super.request.compilationResult();
        }

    }

    public abstract static class FrontEndOnly extends GraalCompilerState {

        @Setup(Level.Trial)
        public void init() {
            initializeMethod();
        }

        @Setup(Level.Invocation)
        public void setup() {
            prepareRequest();
        }

        public StructuredGraph compile() {
            emitFrontEnd();
            return super.graph;
        }

    }

    public abstract static class BackEndOnly extends GraalCompilerState {

        @Setup(Level.Trial)
        public void init() {
            initializeMethod();
        }

        /**
         * Cannot do this {@link Level#Trial only once} since {@link #emitCode()} closes the
         * {@link CompilationResult}.
         */
        @Setup(Level.Invocation)
        public void setupGraph() {
            prepareRequest();
            emitFrontEnd();
        }

        public CompilationResult compile() {
            emitBackEnd();
            return super.request.compilationResult();
        }
    }

    public abstract static class PreAllocationStage extends GraalCompilerState {
        /**
         * No need to rebuild the graph for every invocation since it is not altered by the backend.
         */
        @Setup(Level.Trial)
        public void setupGraph() {
            initializeMethod();
            prepareRequest();
            emitFrontEnd();
        }

        @Setup(Level.Invocation)
        public void setup() {
            generateLIR();
        }

        public LIRGenerationResult compile() {
            preAllocationStage();
            return super.lirGenRes;
        }
    }

    public abstract static class AllocationStage extends GraalCompilerState {
        /**
         * No need to rebuild the graph for every invocation since it is not altered by the backend.
         */
        @Setup(Level.Trial)
        public void setupGraph() {
            initializeMethod();
            prepareRequest();
            emitFrontEnd();
        }

        @Setup(Level.Invocation)
        public void setup() {
            generateLIR();
            preAllocationStage();
        }

        public LIRGenerationResult compile() {
            allocationStage();
            return super.lirGenRes;
        }
    }

    public abstract static class PostAllocationStage extends GraalCompilerState {
        /**
         * No need to rebuild the graph for every invocation since it is not altered by the backend.
         */
        @Setup(Level.Trial)
        public void setupGraph() {
            initializeMethod();
            prepareRequest();
            emitFrontEnd();
        }

        @Setup(Level.Invocation)
        public void setup() {
            generateLIR();
            preAllocationStage();
            allocationStage();
        }

        public LIRGenerationResult compile() {
            postAllocationStage();
            return super.lirGenRes;
        }
    }
}
