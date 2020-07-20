/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import static org.graalvm.compiler.truffle.common.TruffleOutputGroup.GROUP_ID;
import static org.graalvm.compiler.truffle.runtime.TruffleDebugOptions.PrintGraph;
import static org.graalvm.compiler.truffle.runtime.TruffleDebugOptions.PrintGraphTarget.Disable;

import java.io.CharArrayWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompilation;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.common.TruffleOutputGroup;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ExceptionAction;
import org.graalvm.compiler.truffle.runtime.BackgroundCompileQueue.Priority;
import org.graalvm.compiler.truffle.runtime.debug.JFRListener;
import org.graalvm.compiler.truffle.runtime.debug.StatisticsListener;
import org.graalvm.compiler.truffle.runtime.debug.TraceASTCompilationListener;
import org.graalvm.compiler.truffle.runtime.debug.TraceCallTreeListener;
import org.graalvm.compiler.truffle.runtime.debug.TraceCompilationListener;
import org.graalvm.compiler.truffle.runtime.debug.TraceCompilationPolymorphismListener;
import org.graalvm.compiler.truffle.runtime.debug.TraceInliningListener;
import org.graalvm.compiler.truffle.runtime.debug.TraceSplittingListener;
import org.graalvm.compiler.truffle.runtime.serviceprovider.TruffleRuntimeServices;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.OptimizationFailedException;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.AbstractAssumption;
import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.SlowPathException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.LayoutFactory;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.stack.InspectedFrame;
import jdk.vm.ci.code.stack.InspectedFrameVisitor;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.services.Services;

/**
 * Implementation of the Truffle runtime when running on top of Graal. There is only one per VM.
 */
public abstract class GraalTruffleRuntime implements TruffleRuntime, TruffleCompilerRuntime {

    private static final int JAVA_SPECIFICATION_VERSION = getJavaSpecificationVersion();
    private static final boolean Java8OrEarlier = JAVA_SPECIFICATION_VERSION <= 8;

    /**
     * Used only to reset state for native image compilation.
     */
    protected void clearState() {
        assert TruffleOptions.AOT : "Must be called only in AOT mode.";
        callMethods = null;
    }

    private final GraalTruffleRuntimeListenerDispatcher listeners = new GraalTruffleRuntimeListenerDispatcher();

    protected volatile TruffleCompiler truffleCompiler;
    protected LoopNodeFactory loopNodeFactory;
    protected CallMethods callMethods;

    private final GraalTVMCI tvmci = new GraalTVMCI();

    private volatile GraalTestTVMCI testTvmci;

    /**
     * Option values initialized from Truffle compiler runtime.
     */
    @NativeImageReinitialize private volatile Map<String, Object> initialOptions;

    /**
     * Utility method that casts the singleton {@link TruffleRuntime}.
     */
    public static GraalTruffleRuntime getRuntime() {
        return (GraalTruffleRuntime) Truffle.getRuntime();
    }

    private final UnmodifiableEconomicMap<String, Class<?>> lookupTypes;

    public GraalTruffleRuntime(Iterable<Class<?>> extraLookupTypes) {
        this.lookupTypes = initLookupTypes(extraLookupTypes);
    }

    @Override
    public String getName() {
        String compilerConfigurationName = getCompilerConfigurationName();
        assert compilerConfigurationName != null;
        String suffix;
        if (compilerConfigurationName == null) {
            suffix = "Unknown";
        } else if (compilerConfigurationName.equals("community")) {
            suffix = "CE";
        } else if (compilerConfigurationName.equals("enterprise")) {
            suffix = "EE";
        } else {
            assert false : "unexpected compiler configuration name: " + compilerConfigurationName;
            suffix = compilerConfigurationName;
        }
        return "GraalVM " + suffix;
    }

    /**
     * This method allows retrieval of the compiler configuration without requiring to initialize
     * the {@link TruffleCompiler} with
     * {@link #getTruffleCompiler(org.graalvm.compiler.truffle.common.CompilableTruffleAST)
     * getTruffleCompiler}. The result of this method should always match
     * {@link TruffleCompiler#getCompilerConfigurationName()}.
     */
    protected abstract String getCompilerConfigurationName();

    protected GraalTVMCI getTvmci() {
        return tvmci;
    }

    protected TVMCI.Test<?, ?> getTestTvmci() {
        if (testTvmci == null) {
            synchronized (this) {
                if (testTvmci == null) {
                    testTvmci = new GraalTestTVMCI(this);
                }
            }
        }
        return testTvmci;
    }

    @Override
    public TruffleInlining createInliningPlan(CompilableTruffleAST compilable, TruffleCompilationTask task) {
        final OptimizedCallTarget sourceTarget = (OptimizedCallTarget) compilable;
        final TruffleInliningPolicy policy;
        if (task != null && task.isLastTier() && sourceTarget.getOptionValue(PolyglotCompilerOptions.Inlining)) {
            policy = TruffleInliningPolicy.getInliningPolicy();
        } else {
            policy = TruffleInliningPolicy.getNoInliningPolicy();
        }
        return new TruffleInlining(sourceTarget, policy);
    }

    @Override
    public CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
        return asObject(OptimizedCallTarget.class, constant);
    }

    @Override
    public JavaConstant getCallTargetForCallNode(JavaConstant callNodeConstant) {
        OptimizedDirectCallNode callNode = asObject(OptimizedDirectCallNode.class, callNodeConstant);
        return forObject(callNode.getCallTarget());
    }

    @Override
    public Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumptionConstant) {
        final OptimizedAssumption optimizedAssumption = asObject(OptimizedAssumption.class, optimizedAssumptionConstant);
        return optimizedAssumption.registerDependency();
    }

    @Override
    public final Map<String, Object> getOptions() {
        Map<String, Object> res = initialOptions;
        if (res == null) {
            synchronized (this) {
                res = initialOptions;
                if (res == null) {
                    res = createInitialOptions();
                    initialOptions = res;
                }
            }
        }
        return res;
    }

    protected abstract JavaConstant forObject(Object object);

    protected abstract <T> T asObject(Class<T> type, JavaConstant constant);

    protected abstract Map<String, Object> createInitialOptions();

    public abstract TruffleCompiler newTruffleCompiler();

    private static <T extends PrioritizedServiceProvider> T loadPrioritizedServiceProvider(Class<T> clazz) {
        Iterable<T> providers = TruffleRuntimeServices.load(clazz);
        T bestFactory = null;
        for (T factory : providers) {
            if (bestFactory == null) {
                bestFactory = factory;
            } else if (factory.getPriority() > bestFactory.getPriority()) {
                bestFactory = factory;
            }
        }
        if (bestFactory == null) {
            throw new IllegalStateException("Unable to load a factory for " + clazz.getName());
        }
        return bestFactory;
    }

    @Override
    public ConstantFieldInfo getConstantFieldInfo(ResolvedJavaField field) {
        for (Annotation a : getAnnotations(field)) {
            if (a.annotationType() == Child.class) {
                return TruffleCompilerRuntime.ConstantFieldInfo.CHILD;
            }
            if (a.annotationType() == Children.class) {
                return TruffleCompilerRuntime.ConstantFieldInfo.CHILDREN;
            }
            if (a.annotationType() == CompilationFinal.class) {
                CompilationFinal cf = (CompilationFinal) a;
                int dimensions = actualStableDimensions(field, cf.dimensions());
                return TruffleCompilerRuntime.ConstantFieldInfo.forDimensions(dimensions);
            }

        }
        return null;
    }

    private static int actualStableDimensions(ResolvedJavaField field, int dimensions) {
        if (dimensions == 0) {
            return 0;
        }
        int arrayDim = getArrayDimensions(field.getType());
        if (dimensions < 0) {
            if (dimensions != -1) {
                throw new IllegalArgumentException("Negative @CompilationFinal dimensions");
            }
            return arrayDim;
        }
        if (dimensions > arrayDim) {
            throw new IllegalArgumentException(String.format("@CompilationFinal(dimensions=%d) exceeds declared array dimensions (%d) of field %s", dimensions, arrayDim, field));
        }
        return dimensions;
    }

    private static int getArrayDimensions(JavaType type) {
        int dimensions = 0;
        for (JavaType componentType = type; componentType.isArray(); componentType = componentType.getComponentType()) {
            dimensions++;
        }
        return dimensions;
    }

    @SuppressWarnings("deprecation")
    @Override
    public LoopExplosionKind getLoopExplosionKind(ResolvedJavaMethod method) {
        ExplodeLoop explodeLoop = getAnnotation(ExplodeLoop.class, method);
        if (explodeLoop == null) {
            return LoopExplosionKind.NONE;
        }

        switch (explodeLoop.kind()) {
            case FULL_UNROLL:
                return LoopExplosionKind.FULL_UNROLL;
            case FULL_UNROLL_UNTIL_RETURN:
                return LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN;
            case FULL_EXPLODE:
                return LoopExplosionKind.FULL_EXPLODE;
            case FULL_EXPLODE_UNTIL_RETURN:
                return LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN;
            case MERGE_EXPLODE:
                return LoopExplosionKind.MERGE_EXPLODE;
            default:
                throw new InternalError(String.format("Unknown Truffle LoopExplosionKind %s", explodeLoop.kind()));
        }
    }

    private static UnmodifiableEconomicMap<String, Class<?>> initLookupTypes(Iterable<Class<?>> extraTypes) {
        EconomicMap<String, Class<?>> m = EconomicMap.create();
        for (Class<?> c : new Class<?>[]{
                        Node.class,
                        UnexpectedResultException.class,
                        SlowPathException.class,
                        OptimizedCallTarget.class,
                        OptimizedDirectCallNode.class,
                        OptimizedAssumption.class,
                        CompilerDirectives.class,
                        GraalCompilerDirectives.class,
                        InlineDecision.class,
                        CompilerAsserts.class,
                        ExactMath.class,
                        ArrayUtils.class,
                        FrameDescriptor.class,
                        FrameSlot.class,
                        FrameSlotKind.class,
                        MethodHandle.class,
                        ArrayList.class,
                        FrameSlotKind.class,
                        AbstractAssumption.class,
                        MaterializedFrame.class,
                        FrameWithoutBoxing.class,
                        BranchProfile.class,
                        ConditionProfile.class,
                        Objects.class,
        }) {
            m.put(c.getName(), c);
        }
        for (Class<?> c : extraTypes) {
            m.put(c.getName(), c);
        }
        for (TruffleTypes s : TruffleRuntimeServices.load(TruffleTypes.class)) {
            for (Class<?> c : s.getTypes()) {
                m.put(c.getName(), c);
            }
        }
        return m;
    }

    @Override
    public ResolvedJavaType resolveType(MetaAccessProvider metaAccess, String className, boolean required) {
        Class<?> c = lookupTypes.get(className);
        if (c == null) {
            if (!required) {
                return null;
            }
            throw new NoClassDefFoundError(className);
        }
        return metaAccess.lookupJavaType(c);
    }

    protected void installDefaultListeners() {
        TraceCompilationListener.install(this);
        TraceCompilationPolymorphismListener.install(this);
        TraceCallTreeListener.install(this);
        TraceInliningListener.install(this);
        TraceSplittingListener.install(this);
        StatisticsListener.install(this);
        TraceASTCompilationListener.install(this);
        JFRListener.install(this);
        TruffleSplittingStrategy.installListener(this);
        installShutdownHooks();
    }

    protected void installShutdownHooks() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    protected void lookupCallMethods(MetaAccessProvider metaAccess) {
        callMethods = CallMethods.lookup(metaAccess);
    }

    /** Accessor for non-public state in {@link FrameDescriptor}. */
    public void markFrameMaterializeCalled(FrameDescriptor descriptor) {
        GraalRuntimeAccessor.FRAME.markMaterializeCalled(descriptor);
    }

    /** Accessor for non-public state in {@link FrameDescriptor}. */
    public boolean getFrameMaterializeCalled(FrameDescriptor descriptor) {
        return GraalRuntimeAccessor.FRAME.getMaterializeCalled(descriptor);
    }

    @Override
    public LoopNode createLoopNode(RepeatingNode repeatingNode) {
        if (!(repeatingNode instanceof Node)) {
            throw new IllegalArgumentException("Repeating node must be of type Node.");
        }
        return getLoopNodeFactory().create(repeatingNode);
    }

    protected LoopNodeFactory getLoopNodeFactory() {
        if (loopNodeFactory == null) {
            loopNodeFactory = loadPrioritizedServiceProvider(LoopNodeFactory.class);
        }
        return loopNodeFactory;
    }

    @Override
    public DirectCallNode createDirectCallNode(CallTarget target) {
        if (target instanceof OptimizedCallTarget) {
            OptimizedCallTarget optimizedTarget = (OptimizedCallTarget) target;
            final OptimizedDirectCallNode directCallNode = new OptimizedDirectCallNode(optimizedTarget);
            optimizedTarget.addDirectCallNode(directCallNode);
            return directCallNode;
        } else {
            throw new IllegalStateException(String.format("Unexpected call target class %s!", target.getClass()));
        }
    }

    @Override
    public IndirectCallNode createIndirectCallNode() {
        return new OptimizedIndirectCallNode();
    }

    @Override
    public VirtualFrame createVirtualFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return OptimizedCallTarget.createFrame(frameDescriptor, arguments);
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments) {
        return createMaterializedFrame(arguments, new FrameDescriptor());
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return new FrameWithoutBoxing(frameDescriptor, arguments);
    }

    @Override
    public CompilerOptions createCompilerOptions() {
        return new GraalCompilerOptions();
    }

    @Override
    public Assumption createAssumption() {
        return createAssumption(null);
    }

    @Override
    public Assumption createAssumption(String name) {
        return new OptimizedAssumption(name);
    }

    public GraalTruffleRuntimeListener getListener() {
        return listeners;
    }

    @TruffleBoundary
    @Override
    public <T> T iterateFrames(final FrameInstanceVisitor<T> visitor) {
        return iterateImpl(visitor, 0);
    }

    private static final class FrameVisitor<T> implements InspectedFrameVisitor<T> {

        private final FrameInstanceVisitor<T> visitor;
        private final CallMethods methods;

        private int skipFrames;

        private InspectedFrame callNodeFrame;

        FrameVisitor(FrameInstanceVisitor<T> visitor, CallMethods methods, int skip) {
            this.visitor = visitor;
            this.methods = methods;
            this.skipFrames = skip;
        }

        @Override
        public T visitFrame(InspectedFrame frame) {
            if (frame.isMethod(methods.callOSRMethod)) {
                // we ignore OSR frames.
                skipFrames++;
                return null;
            } else if (frame.isMethod(methods.callTargetMethod)) {
                if (skipFrames == 0) {
                    try {
                        return visitor.visitFrame(new GraalFrameInstance(frame, callNodeFrame));
                    } finally {
                        callNodeFrame = null;
                    }
                } else {
                    skipFrames--;
                }
            } else if (frame.isMethod(methods.callDirectMethod) || frame.isMethod(methods.callIndirectMethod) || frame.isMethod(methods.callInlinedMethod) ||
                            frame.isMethod(methods.callInlinedCallMethod)) {
                callNodeFrame = frame;
            }
            return null;
        }
    }

    private <T> T iterateImpl(FrameInstanceVisitor<T> visitor, final int skip) {
        CallMethods methods = getCallMethods();
        FrameVisitor<T> jvmciVisitor = new FrameVisitor<>(visitor, methods, skip);
        return getStackIntrospection().iterateFrames(methods.anyFrameMethod, methods.anyFrameMethod, 0, jvmciVisitor);
    }

    protected abstract StackIntrospection getStackIntrospection();

    @Override
    public FrameInstance getCallerFrame() {
        return iterateImpl(frame -> frame, 1);
    }

    @TruffleBoundary
    @Override
    public FrameInstance getCurrentFrame() {
        return iterateImpl(frame -> frame, 0);
    }

    @Override
    public <T> T getCapability(Class<T> capability) {
        if (capability == TVMCI.class) {
            return capability.cast(tvmci);
        } else if (capability == LayoutFactory.class) {
            LayoutFactory layoutFactory = loadObjectLayoutFactory();
            GraalRuntimeAccessor.JDK.exportTo(layoutFactory.getClass());
            return capability.cast(layoutFactory);
        } else if (capability == TVMCI.Test.class) {
            return capability.cast(getTestTvmci());
        }
        try {
            Iterator<T> services = TruffleRuntimeServices.load(capability).iterator();
            if (services.hasNext()) {
                return services.next();
            }
            return null;
        } catch (ServiceConfigurationError e) {
            // Happens on JDK 9 when a service type has not been exported to Graal
            // or Graal's module descriptor does not declare a use of capability.
            return null;
        }
    }

    public abstract SpeculationLog createSpeculationLog();

    @Override
    public final RootCallTarget createCallTarget(RootNode rootNode) {
        CompilerAsserts.neverPartOfCompilation();
        final OptimizedCallTarget newCallTarget = createClonedCallTarget(rootNode, null);
        TruffleSplittingStrategy.newTargetCreated(newCallTarget);
        return newCallTarget;
    }

    public final OptimizedCallTarget createClonedCallTarget(RootNode rootNode, OptimizedCallTarget source) {
        CompilerAsserts.neverPartOfCompilation();
        OptimizedCallTarget target = createOptimizedCallTarget(source, rootNode);
        GraalRuntimeAccessor.INSTRUMENT.onLoad(target.getRootNode());
        return target;
    }

    public final OptimizedCallTarget createOSRCallTarget(RootNode rootNode) {
        CompilerAsserts.neverPartOfCompilation();
        OptimizedCallTarget target = createOptimizedCallTarget(null, rootNode);
        GraalRuntimeAccessor.INSTRUMENT.onLoad(target.getRootNode());
        return target;
    }

    public abstract OptimizedCallTarget createOptimizedCallTarget(OptimizedCallTarget source, RootNode rootNode);

    public void addListener(GraalTruffleRuntimeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(GraalTruffleRuntimeListener listener) {
        listeners.remove(listener);
    }

    private void shutdown() {
        getListener().onShutdown();
        TruffleCompiler tcp = truffleCompiler;
        if (tcp != null) {
            tcp.shutdown();
        }
    }

    void onEngineClosed(EngineData runtimeData) {
        getListener().onEngineClosed(runtimeData);
    }

    protected void doCompile(OptimizedCallTarget callTarget, TruffleCompilationTask task) {
        List<OptimizedCallTarget> blockCompilations = OptimizedBlockNode.preparePartialBlockCompilations(callTarget);
        for (OptimizedCallTarget blockTarget : blockCompilations) {
            if (blockTarget.isValid()) {
                continue;
            }
            compileImpl(blockTarget, task);
        }
        compileImpl(callTarget, task);
    }

    @SuppressWarnings("try")
    private void compileImpl(OptimizedCallTarget callTarget, TruffleCompilationTask task) {
        boolean compilationStarted = false;
        try {
            TruffleCompiler compiler = getTruffleCompiler(callTarget);
            try (TruffleCompilation compilation = compiler.openCompilation(callTarget)) {
                final Map<String, Object> optionsMap = TruffleRuntimeOptions.getOptionsForCompiler(callTarget);
                try (TruffleDebugContext debug = compiler.openDebugContext(optionsMap, compilation)) {
                    compilationStarted = true;
                    listeners.onCompilationStarted(callTarget);
                    TruffleInlining inlining = createInliningPlan(callTarget, task);
                    try (AutoCloseable s = debug.scope("Truffle", new TruffleDebugJavaMethod(callTarget))) {
                        // Open the "Truffle::methodName" dump group if dumping is enabled.
                        try (TruffleOutputGroup o = TruffleDebugOptions.getValue(PrintGraph) == Disable ? null
                                        : TruffleOutputGroup.open(debug, callTarget, Collections.singletonMap(GROUP_ID, compilation))) {
                            // Create "AST" and "Call Tree" groups if dumping is enabled.
                            maybeDumpTruffleTree(debug, callTarget, inlining);
                            // Compile the method (puts dumps in "Graal Graphs" group if dumping is
                            // enabled).
                            compiler.doCompile(debug, compilation, optionsMap, inlining, task, listeners.isEmpty() ? null : listeners);
                        }
                    } finally {
                        if (debug != null) {
                            /*
                             * The graph dumping code of Graal might leave inlining dump groups
                             * open, in case there are more graphs coming. Close these groups at the
                             * end of the compilation.
                             */
                            debug.closeDebugChannels();
                        }
                    }
                    // used by legacy inlining
                    dequeueInlinedCallSites(inlining, callTarget);
                    // used by language-agnostic inlining
                    inlining.dequeueTargets();
                }
            }
        } catch (OptimizationFailedException e) {
            // Listeners already notified
            throw e;
        } catch (RuntimeException | Error e) {
            notifyCompilationFailure(callTarget, e, compilationStarted);
            throw e;
        } catch (Throwable e) {
            notifyCompilationFailure(callTarget, e, compilationStarted);
            throw new InternalError(e);
        }
    }

    private void notifyCompilationFailure(OptimizedCallTarget callTarget, Throwable t, boolean compilationStarted) {
        try {
            if (compilationStarted) {
                listeners.onCompilationFailed(callTarget, t.toString(), false, false);
            } else {
                listeners.onCompilationDequeued(callTarget, this, String.format("Failed to create Truffle compiler due to %s.", t.getMessage()));
            }
        } finally {
            callTarget.onCompilationFailed(() -> CompilableTruffleAST.serializeException(t), false, false);
        }
    }

    @SuppressWarnings("try")
    private static void maybeDumpTruffleTree(TruffleDebugContext debug, OptimizedCallTarget callTarget, TruffleInlining inlining) throws Exception {
        try (AutoCloseable c = debug.scope("TruffleTree")) {
            if (debug.isDumpEnabled()) {
                TruffleTreeDumper.dump(debug, callTarget, inlining);
            }
        }
    }

    private static void dequeueInlinedCallSites(TruffleInlining inliningDecision, OptimizedCallTarget optimizedCallTarget) {
        if (inliningDecision != null) {
            for (TruffleInliningDecision decision : inliningDecision) {
                if (decision.shouldInline()) {
                    OptimizedCallTarget target = decision.getTarget();
                    if (target != optimizedCallTarget) {
                        target.cancelCompilation("Inlining caller compiled.");
                    }
                    dequeueInlinedCallSites(decision, optimizedCallTarget);
                }
            }
        }
    }

    protected abstract BackgroundCompileQueue getCompileQueue();

    @SuppressWarnings("try")
    public CancellableCompileTask submitForCompilation(OptimizedCallTarget optimizedCallTarget, boolean lastTierCompilation) {
        Priority priority = lastTierCompilation ? Priority.LAST_TIER : Priority.FIRST_TIER;
        return getCompileQueue().submitTask(priority, optimizedCallTarget, new BackgroundCompileQueue.Request() {
            @Override
            protected void execute(CancellableCompileTask task, WeakReference<OptimizedCallTarget> targetRef) {
                OptimizedCallTarget callTarget = targetRef.get();
                if (callTarget != null && task.start()) {
                    try {
                        doCompile(callTarget, task);
                    } finally {
                        task.finished();
                    }
                }
            }
        });

    }

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    public void finishCompilation(OptimizedCallTarget optimizedCallTarget, CancellableCompileTask task, boolean mayBeAsynchronous) {

        if (!mayBeAsynchronous) {
            try {
                uninterruptibleWaitForCompilation(task);
            } catch (ExecutionException e) {
                if (optimizedCallTarget.engine.compilationFailureAction == ExceptionAction.Throw) {
                    throw new RuntimeException(e.getCause());
                } else {
                    if (assertionsEnabled()) {
                        e.printStackTrace();
                    } else {
                        // silently ignored
                    }
                }
            }
        }
    }

    private static void uninterruptibleWaitForCompilation(CancellableCompileTask task) throws ExecutionException {
        // We want to keep the interrupt bit if we are interrupted.
        // But we also want to maintain the semantics of foreground compilation:
        // waiting for the compilation to finish, even if it takes long,
        // so that compilation errors or effects are still properly waited for.
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    task.awaitCompletion();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void waitForCompilation(OptimizedCallTarget optimizedCallTarget, long timeout) throws ExecutionException, TimeoutException {
        CancellableCompileTask task = optimizedCallTarget.getCompilationTask();
        if (task != null && !task.isCancelled()) {
            try {
                task.awaitCompletion(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // ignore interrupted
            }
        }

    }

    public int getCompilationQueueSize() {
        return getCompileQueue().getQueueSize();
    }

    /**
     * Notifies this runtime when a Truffle AST is being executed in the Truffle interpreter even
     * though compiled code is available for the AST.
     *
     * If this runtime uses a special entry point to switch to compiled Truffle code, then this
     * means the code with the special entry point was deoptimized or otherwise removed from the
     * code cache and needs to be re-installed.
     */
    @SuppressWarnings("unused")
    public void bypassedInstalledCode(OptimizedCallTarget target) {
    }

    protected CallMethods getCallMethods() {
        return callMethods;
    }

    /**
     * Use {@link OptimizedCallTarget#engine} whenever possible as it's much faster.
     */
    protected static EngineData getEngineData(RootNode rootNode) {
        return GraalTVMCI.getEngineData(rootNode);
    }

    private static LayoutFactory loadObjectLayoutFactory() {
        ServiceLoader<LayoutFactory> graalLoader = ServiceLoader.load(LayoutFactory.class, GraalTruffleRuntime.class.getClassLoader());
        if (Java8OrEarlier) {
            return selectObjectLayoutFactory(Collections.singleton(graalLoader));
        } else {
            /*
             * The Graal module (i.e., jdk.internal.vm.compiler) is loaded by the platform class
             * loader on JDK 9+. Its module dependencies such as Truffle are supplied via
             * --module-path which means they are loaded by the app class loader. As such, we need
             * to search the app class loader path as well.
             */
            ServiceLoader<LayoutFactory> appLoader = ServiceLoader.load(LayoutFactory.class, LayoutFactory.class.getClassLoader());
            return selectObjectLayoutFactory(Arrays.asList(graalLoader, appLoader));
        }
    }

    protected static LayoutFactory selectObjectLayoutFactory(Iterable<Iterable<LayoutFactory>> availableLayoutFactories) {
        String layoutFactoryImplName = Services.getSavedProperties().get("truffle.object.LayoutFactory");
        LayoutFactory bestLayoutFactory = null;
        for (Iterable<LayoutFactory> currentLayoutFactories : availableLayoutFactories) {
            for (LayoutFactory currentLayoutFactory : currentLayoutFactories) {
                if (layoutFactoryImplName != null) {
                    if (currentLayoutFactory.getClass().getName().equals(layoutFactoryImplName)) {
                        return currentLayoutFactory;
                    }
                } else {
                    if (bestLayoutFactory == null) {
                        bestLayoutFactory = currentLayoutFactory;
                    } else if (currentLayoutFactory.getPriority() >= bestLayoutFactory.getPriority()) {
                        assert currentLayoutFactory.getPriority() != bestLayoutFactory.getPriority();
                        bestLayoutFactory = currentLayoutFactory;
                    }
                }
            }
        }
        return bestLayoutFactory;
    }

    protected String printStackTraceToString(Throwable e) {
        CharArrayWriter caw = new CharArrayWriter();
        e.printStackTrace(new PrintWriter(caw));
        return caw.toString();
    }

    private static int getJavaSpecificationVersion() {
        String value = Services.getSavedProperties().get("java.specification.version");
        if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        return Integer.parseInt(value);
    }

    protected static final class CallMethods {
        public final ResolvedJavaMethod callDirectMethod;
        public final ResolvedJavaMethod callInlinedMethod;
        public final ResolvedJavaMethod callIndirectMethod;
        public final ResolvedJavaMethod callTargetMethod;
        public final ResolvedJavaMethod callOSRMethod;
        public final ResolvedJavaMethod callInlinedCallMethod;
        public final ResolvedJavaMethod[] anyFrameMethod;

        private CallMethods(MetaAccessProvider metaAccess) {
            this.callDirectMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_DIRECT);
            this.callIndirectMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_INDIRECT);
            this.callInlinedMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_INLINED);
            this.callInlinedCallMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_INLINED_CALL);
            this.callTargetMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_TARGET_METHOD);
            this.callOSRMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_OSR_METHOD);
            this.anyFrameMethod = new ResolvedJavaMethod[]{callDirectMethod, callIndirectMethod, callInlinedMethod, callTargetMethod, callOSRMethod, callInlinedCallMethod};
        }

        public static CallMethods lookup(MetaAccessProvider metaAccess) {
            return new CallMethods(metaAccess);
        }
    }

    @Override
    public boolean isValueType(ResolvedJavaType type) {
        return getAnnotation(CompilerDirectives.ValueType.class, type) != null;
    }

    @Override
    public JavaKind getJavaKindForFrameSlotKind(int frameSlotKindTag) {
        if (frameSlotKindTag == FrameSlotKind.Boolean.tag) {
            return JavaKind.Boolean;
        }
        if (frameSlotKindTag == FrameSlotKind.Byte.tag) {
            return JavaKind.Byte;
        }
        if (frameSlotKindTag == FrameSlotKind.Int.tag) {
            return JavaKind.Int;
        }
        if (frameSlotKindTag == FrameSlotKind.Float.tag) {
            return JavaKind.Float;
        }
        if (frameSlotKindTag == FrameSlotKind.Long.tag) {
            return JavaKind.Long;
        }
        if (frameSlotKindTag == FrameSlotKind.Double.tag) {
            return JavaKind.Double;
        }
        if (frameSlotKindTag == FrameSlotKind.Object.tag) {
            return JavaKind.Object;
        }
        if (frameSlotKindTag == FrameSlotKind.Illegal.tag) {
            return JavaKind.Illegal;
        }
        throw new IllegalArgumentException("Unknown FrameSlotKind tag: " + frameSlotKindTag);
    }

    @Override
    public int getFrameSlotKindTagForJavaKind(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return FrameSlotKind.Boolean.tag;
            case Byte:
                return FrameSlotKind.Byte.tag;
            case Int:
                return FrameSlotKind.Int.tag;
            case Float:
                return FrameSlotKind.Float.tag;
            case Long:
                return FrameSlotKind.Long.tag;
            case Double:
                return FrameSlotKind.Double.tag;
            case Object:
                return FrameSlotKind.Object.tag;
            case Illegal:
                return FrameSlotKind.Illegal.tag;
        }
        throw new IllegalArgumentException("No FrameSlotKind for Java kind " + kind);
    }

    @Override
    public int getFrameSlotKindTagsCount() {
        return FrameSlotKind.values().length;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InlineKind getInlineKind(ResolvedJavaMethod original, boolean duringPartialEvaluation) {
        TruffleBoundary truffleBoundary = getAnnotation(TruffleBoundary.class, original);
        if (truffleBoundary != null) {
            if (duringPartialEvaluation) {
                // Since this method is invoked by the bytecode parser plugins, which can be invoked
                // by the partial evaluator, we want to prevent inlining across the boundary during
                // partial evaluation,
                // even if the TruffleBoundary allows inlining after partial evaluation.
                if (truffleBoundary.transferToInterpreterOnException()) {
                    return InlineKind.DO_NOT_INLINE_WITH_SPECULATIVE_EXCEPTION;
                } else {
                    return InlineKind.DO_NOT_INLINE_WITH_EXCEPTION;
                }
            } else if (!truffleBoundary.allowInlining()) {
                return InlineKind.DO_NOT_INLINE_WITH_EXCEPTION;
            }
        } else if (getAnnotation(TruffleCallBoundary.class, original) != null) {
            return InlineKind.DO_NOT_INLINE_WITH_EXCEPTION;
        }
        return InlineKind.INLINE;
    }

    @Override
    public boolean isTruffleBoundary(ResolvedJavaMethod method) {
        return getAnnotation(TruffleBoundary.class, method) != null;
    }

    @Override
    public void log(CompilableTruffleAST compilable, String message) {
        ((OptimizedCallTarget) compilable).engine.getLogger().log(Level.INFO, message);
    }

    protected abstract OutputStream getDefaultLogStream();

    // https://bugs.openjdk.java.net/browse/JDK-8209535

    private static BailoutException handleAnnotationFailure(NoClassDefFoundError e, String attemptedAction) {
        throw new BailoutException(e, "Error while %s. " +
                        "This usually means that the unresolved type is in the signature of some other " +
                        "method or field in the same class. This can be resolved by modifying the relevant class path " +
                        "or module path such that it includes the missing type.",
                        attemptedAction);
    }

    private static Annotation[] getAnnotations(ResolvedJavaField element) {
        try {
            return element.getAnnotations();
        } catch (NoClassDefFoundError e) {
            throw handleAnnotationFailure(e, String.format("querying %s for annotations", element.format("%H.%n:%t")));
        }
    }

    private static <T extends Annotation> T getAnnotation(Class<T> annotationClass, ResolvedJavaMethod method) {
        try {
            return annotationClass.cast(method.getAnnotation(annotationClass));
        } catch (NoClassDefFoundError e) {
            throw handleAnnotationFailure(e, String.format("querying %s for presence of a %s annotation", method.format("%H.%n(%p)"), annotationClass.getName()));
        }
    }

    private static <T extends Annotation> T getAnnotation(Class<T> annotationClass, ResolvedJavaType type) {
        try {
            return annotationClass.cast(type.getAnnotation(annotationClass));
        } catch (NoClassDefFoundError e) {
            throw handleAnnotationFailure(e, String.format("querying %s for presence of a %s annotation", type.toJavaName(), annotationClass.getName()));
        }
    }

    /**
     * Gets a closeable that will be used in a try-with-resources statement surrounding the run-loop
     * of a Truffle compiler thread. In conjunction with
     * {@link #getCompilerIdleDelay(OptimizedCallTarget)}, this can be used to release resources
     * held by idle Truffle compiler threads.
     *
     * If a non-null value is returned, its {@link AutoCloseable#close()} must not throw an
     * exception.
     */
    protected AutoCloseable openCompilerThreadScope() {
        return null;
    }

    /**
     * Gets the time in milliseconds an idle Truffle compiler thread will wait for new tasks before
     * terminating. A value of {@code <= 0} means that Truffle compiler threads block indefinitely
     * waiting for a task and thus never terminate.
     */
    protected long getCompilerIdleDelay(@SuppressWarnings("unused") OptimizedCallTarget callTarget) {
        return 0;
    }
}
