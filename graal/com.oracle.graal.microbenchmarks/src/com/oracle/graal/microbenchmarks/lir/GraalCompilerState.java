package com.oracle.graal.microbenchmarks.lir;

import static com.oracle.graal.microbenchmarks.graal.util.GraalUtil.getGraph;
import static com.oracle.graal.microbenchmarks.graal.util.GraalUtil.getMethodFromMethodSpec;

import java.util.List;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.api.test.Graal;
import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.GraalCompiler;
import com.oracle.graal.compiler.GraalCompiler.Request;
import com.oracle.graal.compiler.LIRGenerationPhase;
import com.oracle.graal.compiler.LIRGenerationPhase.LIRGenerationContext;
import com.oracle.graal.compiler.common.alloc.ComputeBlockOrder;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugEnvironment;
import com.oracle.graal.debug.internal.DebugScope;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.phases.AllocationPhase.AllocationContext;
import com.oracle.graal.lir.phases.LIRPhase;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.microbenchmarks.graal.util.GraalState;
import com.oracle.graal.microbenchmarks.graal.util.MethodSpec;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.ScheduleResult;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.cfg.ControlFlowGraph;
import com.oracle.graal.nodes.spi.LoweringProvider;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.options.DerivedOptionValue;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;
import com.oracle.graal.phases.tiers.TargetProvider;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.runtime.RuntimeProvider;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

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
    private final StructuredGraph originalGraph;

    /**
     * The graph processed by the benchmark.
     */
    private StructuredGraph graph;
    private final Backend backend;
    private final Providers providers;
    private final DerivedOptionValue<Suites> suites;
    private final DerivedOptionValue<LIRSuites> lirSuites;

    /**
     * We only allow inner classes to subclass this to ensure that the {@link Setup} methods are
     * executed in the right order.
     */
    private GraalCompilerState() {
        this.backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        this.providers = backend.getProviders();
        this.suites = new DerivedOptionValue<>(this::createSuites);
        this.lirSuites = new DerivedOptionValue<>(this::createLIRSuites);

        // Ensure a debug configuration for this thread is initialized
        if (Debug.isEnabled() && DebugScope.getConfig() == null) {
            DebugEnvironment.initialize(System.out);
        }

        GraalState graal = new GraalState();
        ResolvedJavaMethod method = graal.metaAccess.lookupJavaMethod(getMethodFromMethodSpec(getClass()));
        StructuredGraph structuredGraph = null;
        try (Debug.Scope s = Debug.scope("GraphState", method)) {
            structuredGraph = preprocessOriginal(getGraph(graal, method));
        } catch (Throwable t) {
            Debug.handle(t);
        }
        this.originalGraph = structuredGraph;
    }

    protected StructuredGraph preprocessOriginal(StructuredGraph structuredGraph) {
        return structuredGraph;
    }

    protected Suites createSuites() {
        Suites ret = backend.getSuites().getDefaultSuites().copy();
        return ret;
    }

    protected LIRSuites createLIRSuites() {
        LIRSuites ret = backend.getSuites().getDefaultLIRSuites().copy();
        return ret;
    }

    protected Backend getBackend() {
        return backend;
    }

    protected Suites getSuites() {
        return suites.getValue();
    }

    protected LIRSuites getOriginalLIRSuites() {
        return lirSuites.getValue();
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

    private LIRSuites updatedLIRSuites;

    private LIRSuites getUpdatedLIRSuites() {
        if (updatedLIRSuites != null) {
            return updatedLIRSuites;
        }
        return request.lirSuites;
    }

    /**
     * Gets the {@link LIRSuites} of the {@link #request}. Do not alter the suites directly. Use
     * {@link #updateLIRSuite(LIRSuites)} instead.
     */
    protected final LIRSuites getRequestLIRSuites() {
        return request.lirSuites;
    }

    /**
     * Updates {@link LIRSuites} used in {@link #preAllocationStage()}, {@link #allocationStage()}
     * and {@link #postAllocationStage()}.
     *
     * @see #getRequestLIRSuites()
     */
    protected final void updateLIRSuite(LIRSuites newLIRSuites) {
        this.updatedLIRSuites = newLIRSuites;
    }

    private Request<CompilationResult> request;
    private LIRGenerationResult lirGenRes;
    private LIRGeneratorTool lirGenTool;
    private NodeLIRBuilderTool nodeLirGen;
    private RegisterConfig registerConfig;
    private ScheduleResult schedule;
    private List<Block> codeEmittingOrder;
    private List<Block> linearScanOrder;

    /**
     * Copies the {@link #originalGraph original graph} and prepares the {@link #request}.
     *
     * The {@link Suites} can be changed by overriding {@link #createSuites()}. {@link LIRSuites}
     * can be changed by overriding {@link #createLIRSuites()}.
     */
    protected final void prepareRequest() {
        graph = (StructuredGraph) originalGraph.copy();
        assert !graph.isFrozen();
        ResolvedJavaMethod installedCodeOwner = graph.method();
        request = new Request<>(graph, installedCodeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL,
                        graph.getProfilingInfo(), getSuites(), getOriginalLIRSuites(), new CompilationResult(), CompilationResultBuilderFactory.Default);
    }

    /**
     * Executes the high-level (FrontEnd) part of the compiler.
     */
    protected final void emitFrontEnd() {
        GraalCompiler.emitFrontEnd(request.providers, request.backend, request.graph, request.graphBuilderSuite, request.optimisticOpts, request.profilingInfo, request.suites);
        request.graph.freeze();
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
        assert request.graph.isFrozen() : "Graph not frozen.";
        Object stub = null;
        schedule = request.graph.getLastSchedule();
        ControlFlowGraph cfg = deepCopy(schedule.getCFG());
        Block[] blocks = cfg.getBlocks();
        Block startBlock = cfg.getStartBlock();
        assert startBlock != null;
        assert startBlock.getPredecessorCount() == 0;

        codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.length, startBlock);
        linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.length, startBlock);

        LIR lir = new LIR(cfg, linearScanOrder, codeEmittingOrder);
        FrameMapBuilder frameMapBuilder = request.backend.newFrameMapBuilder(registerConfig);
        String compilationUnitName = null;
        lirGenRes = request.backend.newLIRGenerationResult(compilationUnitName, lir, frameMapBuilder, request.graph, stub);
        lirGenTool = request.backend.newLIRGenerator(lirGenRes);
        nodeLirGen = request.backend.newNodeLIRBuilder(request.graph, lirGenTool);
    }

    private static ControlFlowGraph deepCopy(ControlFlowGraph cfg) {
        return ControlFlowGraph.compute(cfg.graph, true, true, true, true);
    }

    /**
     * Executes the {@link LIRGenerationPhase}.
     */
    protected final void lirGeneration() {
        LIRGenerationContext context = new LIRGenerationContext(lirGenTool, nodeLirGen, request.graph, schedule);
        new LIRGenerationPhase().apply(request.backend.getTarget(), lirGenRes, codeEmittingOrder, linearScanOrder, context);
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
     * Executes the {@link PreAllocationStage}.
     *
     * {@link LIRPhase phases} can be changed with {@link #updateLIRSuite(LIRSuites)}.
     */
    protected final void preAllocationStage() {
        PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationContext(lirGenTool);
        getUpdatedLIRSuites().getPreAllocationOptimizationStage().apply(request.backend.getTarget(), lirGenRes, codeEmittingOrder, linearScanOrder, preAllocOptContext);
    }

    /**
     * Executes the {@link AllocationStage}.
     *
     * {@link LIRPhase phases} can be changed with {@link #updateLIRSuite(LIRSuites)}.
     */
    protected final void allocationStage() {
        AllocationContext allocContext = new AllocationContext(lirGenTool.getSpillMoveFactory(), request.backend.newRegisterAllocationConfig(registerConfig));
        getUpdatedLIRSuites().getAllocationStage().apply(request.backend.getTarget(), lirGenRes, codeEmittingOrder, linearScanOrder, allocContext);
    }

    /**
     * Executes the {@link PostAllocationStage}.
     *
     * {@link LIRPhase phases} can be changed with {@link #updateLIRSuite(LIRSuites)}.
     */
    protected final void postAllocationStage() {
        PostAllocationOptimizationContext postAllocOptContext = new PostAllocationOptimizationContext(lirGenTool);
        getUpdatedLIRSuites().getPostAllocationOptimizationStage().apply(request.backend.getTarget(), lirGenRes, codeEmittingOrder, linearScanOrder, postAllocOptContext);
    }

    /**
     * Emits the machine code.
     */
    protected final void emitCode() {
        int bytecodeSize = request.graph.method() == null ? 0 : request.graph.getBytecodeSize();
        request.compilationResult.setHasUnsafeAccess(request.graph.hasUnsafeAccess());
        GraalCompiler.emitCode(request.backend, request.graph.getAssumptions(), request.graph.method(), request.graph.getInlinedMethods(), bytecodeSize, lirGenRes, request.compilationResult,
                        request.installedCodeOwner, request.factory);
    }

    public abstract static class Compile extends GraalCompilerState {

        @Setup(Level.Invocation)
        public void setup() {
            prepareRequest();
        }

        public CompilationResult compile() {
            emitFrontEnd();
            emitBackEnd();
            return super.request.compilationResult;
        }

    }

    public abstract static class FrontEndOnly extends GraalCompilerState {

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
            return super.request.compilationResult;
        }
    }

    public abstract static class PreAllocationStage extends GraalCompilerState {
        /**
         * No need to rebuild the graph for every invocation since it is not altered by the backend.
         */
        @Setup(Level.Trial)
        public void setupGraph() {
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
