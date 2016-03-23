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
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.microbenchmarks.graal.util.GraalState;
import com.oracle.graal.microbenchmarks.graal.util.MethodSpec;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.ScheduleResult;
import com.oracle.graal.nodes.cfg.Block;
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

    protected LIRSuites getLIRSuites() {
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

    private LIRSuites getUpdatedLIRSuites() {
        LIRSuites updatedLIRSuites = updateLIRSuite(request.lirSuites);
        if (updatedLIRSuites != null) {
            return updatedLIRSuites;
        }
        return request.lirSuites;
    }

    /**
     * @param originalLIRSuites the original {@link LIRSuites suites} ({@link #getLIRSuites()}).
     */
    protected LIRSuites updateLIRSuite(LIRSuites originalLIRSuites) {
        return null;
    }

    private Request<CompilationResult> request;
    private LIRGenerationResult lirGenRes;
    private LIRGeneratorTool lirGenTool;
    private NodeLIRBuilderTool nodeLirGen;
    private RegisterConfig registerConfig;
    private ScheduleResult schedule;
    private List<Block> codeEmittingOrder;
    private List<Block> linearScanOrder;

    protected void prepareRequest() {
        graph = (StructuredGraph) originalGraph.copy();
        assert !graph.isFrozen();
        ResolvedJavaMethod installedCodeOwner = graph.method();
        request = new Request<>(graph, installedCodeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL,
                        graph.getProfilingInfo(), getSuites(), getLIRSuites(), new CompilationResult(), CompilationResultBuilderFactory.Default);
    }

    protected void emitFrontEnd() {
        GraalCompiler.emitFrontEnd(request.providers, request.backend, request.graph, request.graphBuilderSuite, request.optimisticOpts, request.profilingInfo, request.suites);
    }

    protected void emitBackEnd() {
        emitLIR();
        emitCode();
    }

    protected void emitLIR() {
        prepareLIR();
        generateLIR();
        emitLowLevel();
    }

    protected void prepareLIR() {
        Object stub = null;
        schedule = request.graph.getLastSchedule();
        Block[] blocks = schedule.getCFG().getBlocks();
        Block startBlock = schedule.getCFG().getStartBlock();
        assert startBlock != null;
        assert startBlock.getPredecessorCount() == 0;

        codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.length, startBlock);
        linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.length, startBlock);

        LIR lir = new LIR(schedule.getCFG(), linearScanOrder, codeEmittingOrder);
        FrameMapBuilder frameMapBuilder = request.backend.newFrameMapBuilder(registerConfig);
        String compilationUnitName = null;
        lirGenRes = request.backend.newLIRGenerationResult(compilationUnitName, lir, frameMapBuilder, request.graph, stub);
        lirGenTool = request.backend.newLIRGenerator(lirGenRes);
        nodeLirGen = request.backend.newNodeLIRBuilder(request.graph, lirGenTool);
    }

    protected void generateLIR() {
        LIRGenerationContext context = new LIRGenerationContext(lirGenTool, nodeLirGen, request.graph, schedule);
        new LIRGenerationPhase().apply(request.backend.getTarget(), lirGenRes, codeEmittingOrder, linearScanOrder, context);
    }

    protected void emitLowLevel() {
        preAllocationStage();
        allocationStage();
        postAllocationStage();
    }

    protected void preAllocationStage() {
        PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationContext(lirGenTool);
        getUpdatedLIRSuites().getPreAllocationOptimizationStage().apply(request.backend.getTarget(), lirGenRes, codeEmittingOrder, linearScanOrder, preAllocOptContext);
    }

    protected void allocationStage() {
        AllocationContext allocContext = new AllocationContext(lirGenTool.getSpillMoveFactory(), request.backend.newRegisterAllocationConfig(registerConfig));
        getUpdatedLIRSuites().getAllocationStage().apply(request.backend.getTarget(), lirGenRes, codeEmittingOrder, linearScanOrder, allocContext);
    }

    protected void postAllocationStage() {
        PostAllocationOptimizationContext postAllocOptContext = new PostAllocationOptimizationContext(lirGenTool);
        getUpdatedLIRSuites().getPostAllocationOptimizationStage().apply(request.backend.getTarget(), lirGenRes, codeEmittingOrder, linearScanOrder, postAllocOptContext);
    }

    protected void emitCode() {
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

        public CompilationResult compile() {
            emitFrontEnd();
            return super.request.compilationResult;
        }

    }

    public abstract static class BackEndOnly extends GraalCompilerState {
        @Setup(Level.Invocation)
        public void prepareFrontEnd() {
            prepareRequest();
            emitFrontEnd();
        }

        public CompilationResult compile() {
            emitBackEnd();
            return super.request.compilationResult;
        }
    }

    public abstract static class PreAllocationStage extends GraalCompilerState {
        @Setup(Level.Invocation)
        public void setup() {
            prepareRequest();
            emitFrontEnd();
            prepareLIR();
            generateLIR();
        }

        public CompilationResult compile() {
            preAllocationStage();
            return super.request.compilationResult;
        }
    }

    public abstract static class AllocationStage extends GraalCompilerState {
        @Setup(Level.Invocation)
        public void setup() {
            prepareRequest();
            emitFrontEnd();
            prepareLIR();
            generateLIR();
            preAllocationStage();
        }

        public CompilationResult compile() {
            allocationStage();
            return super.request.compilationResult;
        }
    }

    public abstract static class PostAllocationStage extends GraalCompilerState {
        @Setup(Level.Invocation)
        public void setup() {
            prepareRequest();
            emitFrontEnd();
            prepareLIR();
            generateLIR();
            preAllocationStage();
            allocationStage();
        }

        public CompilationResult compile() {
            postAllocationStage();
            return super.request.compilationResult;
        }
    }
}
