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

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitchBoundary;
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
import org.graalvm.compiler.truffle.common.TruffleMetaAccessProvider;
import org.graalvm.compiler.truffle.common.TruffleOutputGroup;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ExceptionAction;
import org.graalvm.compiler.truffle.runtime.BackgroundCompileQueue.Priority;
import org.graalvm.compiler.truffle.runtime.debug.JFRListener;
import org.graalvm.compiler.truffle.runtime.debug.StatisticsListener;
import org.graalvm.compiler.truffle.runtime.debug.TraceASTCompilationListener;
import org.graalvm.compiler.truffle.runtime.debug.TraceCompilationListener;
import org.graalvm.compiler.truffle.runtime.debug.TraceCompilationPolymorphismListener;
import org.graalvm.compiler.truffle.runtime.debug.TraceSplittingListener;
import org.graalvm.compiler.truffle.runtime.serviceprovider.TruffleRuntimeServices;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

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
import com.oracle.truffle.api.dsl.Specialization;
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

    protected CallMethods callMethods;

    private final GraalTVMCI tvmci = new GraalTVMCI();
    private volatile GraalTestTVMCI testTvmci;

    /**
     * Utility method that casts the singleton {@link TruffleRuntime}.
     */
    public static GraalTruffleRuntime getRuntime() {
        return (GraalTruffleRuntime) Truffle.getRuntime();
    }

    private final LoopNodeFactory loopNodeFactory;
    private final EngineCacheSupport engineCacheSupport;
    private final UnmodifiableEconomicMap<String, Class<?>> lookupTypes;
    private final OptionDescriptors engineOptions;

    public GraalTruffleRuntime(Iterable<Class<?>> extraLookupTypes) {
        this.lookupTypes = initLookupTypes(extraLookupTypes);
        List<OptionDescriptors> options = new ArrayList<>();
        this.loopNodeFactory = loadGraalRuntimeServiceProvider(LoopNodeFactory.class, options, true);
        EngineCacheSupport support = loadGraalRuntimeServiceProvider(EngineCacheSupport.class, options, false);
        this.engineCacheSupport = support == null ? new EngineCacheSupport.Disabled() : support;
        options.add(PolyglotCompilerOptions.getDescriptors());
        this.engineOptions = OptionDescriptors.createUnion(options.toArray(new OptionDescriptors[options.size()]));
    }

    @Override
    public TruffleMetaAccessProvider createInliningPlan() {
        return new TruffleInlining();
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

    protected abstract JavaConstant forObject(Object object);

    protected abstract <T> T asObject(Class<T> type, JavaConstant constant);

    protected abstract boolean isPrintGraphEnabled();

    public abstract TruffleCompiler newTruffleCompiler();

    private static <T> T loadServiceProvider(Class<T> clazz, boolean failIfNotFound) {
        Iterable<T> providers;
        if (ImageInfo.inImageBuildtimeCode()) {
            providers = ServiceLoader.load(clazz);
        } else {
            providers = TruffleRuntimeServices.load(clazz);
        }
        boolean priorityService = GraalRuntimeServiceProvider.class.isAssignableFrom(clazz);
        T bestFactory = null;
        int bestPriority = 0;
        for (T factory : providers) {
            int currentPriority;
            if (priorityService) {
                currentPriority = ((GraalRuntimeServiceProvider) factory).getPriority();
            } else {
                currentPriority = 0;
            }
            if (bestFactory == null || currentPriority > bestPriority) {
                bestFactory = factory;
                bestPriority = currentPriority;
            }
        }
        if (bestFactory == null && failIfNotFound) {
            throw new IllegalStateException("Unable to load a factory for " + clazz.getName());
        }
        return bestFactory;
    }

    private static <T extends GraalRuntimeServiceProvider> T loadGraalRuntimeServiceProvider(Class<T> clazz, List<OptionDescriptors> descriptors, boolean failIfNotFound) {
        T bestFactory = loadServiceProvider(clazz, failIfNotFound);
        if (descriptors != null && bestFactory != null) {
            OptionDescriptors serviceOptions = bestFactory.getEngineOptions();
            if (serviceOptions != null) {
                descriptors.add(serviceOptions);
            }
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
        if (JAVA_SPECIFICATION_VERSION >= 15) {
            String className = "jdk.internal.access.foreign.MemorySegmentProxy";
            try {
                Class<?> c = Class.forName(className);
                m.put(c.getName(), c);
            } catch (ClassNotFoundException e) {
                throw new NoClassDefFoundError(className);
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
        TraceSplittingListener.install(this);
        StatisticsListener.install(this);
        TraceASTCompilationListener.install(this);
        JFRListener.install(this);
        TruffleSplittingStrategy.installListener(this);
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

    protected final LoopNodeFactory getLoopNodeFactory() {
        return loopNodeFactory;
    }

    public final EngineCacheSupport getEngineCacheSupport() {
        return engineCacheSupport;
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
            return loadServiceProvider(capability, false);
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

    protected final void doCompile(OptimizedCallTarget callTarget, TruffleCompilationTask task) {
        doCompile(null, callTarget, task);
    }

    protected final void doCompile(TruffleDebugContext debug, OptimizedCallTarget callTarget, TruffleCompilationTask task) {
        List<OptimizedCallTarget> oldBlockCompilations = callTarget.blockCompilations;
        if (oldBlockCompilations != null) {
            for (OptimizedCallTarget blockTarget : oldBlockCompilations) {
                if (blockTarget.isValid()) {
                    continue;
                }
                listeners.onCompilationQueued(blockTarget);
                int nodeCount = blockTarget.getNonTrivialNodeCount();
                if (nodeCount > callTarget.engine.getEngineOptions().get(PolyglotCompilerOptions.PartialBlockMaximumSize)) {
                    listeners.onCompilationDequeued(blockTarget, null, "Partial block is too big to be compiled.");
                    continue;
                }
                compileImpl(debug, blockTarget, task);
            }
        }
        compileImpl(debug, callTarget, task);

        if (oldBlockCompilations == null && callTarget.blockCompilations != null) {
            // retry with block compilations
            ((CompilationTask) task).reset();
            listeners.onCompilationQueued(callTarget);
            doCompile(callTarget, task);
        }
    }

    @SuppressWarnings("try")
    private void compileImpl(TruffleDebugContext initialDebug, OptimizedCallTarget callTarget, TruffleCompilationTask task) {
        boolean compilationStarted = false;
        try {
            TruffleCompiler compiler = getTruffleCompiler(callTarget);
            try (TruffleCompilation compilation = compiler.openCompilation(callTarget)) {
                final Map<String, Object> optionsMap = getOptionsForCompiler(callTarget);
                TruffleDebugContext debug = initialDebug;
                if (debug == null) {
                    debug = compiler.openDebugContext(optionsMap, compilation);
                }
                try {
                    compilationStarted = true;
                    listeners.onCompilationStarted(callTarget);
                    TruffleInlining inlining = new TruffleInlining();
                    try (AutoCloseable s = debug.scope("Truffle", new TruffleDebugJavaMethod(callTarget))) {
                        // Open the "Truffle::methodName" dump group if dumping is enabled.
                        try (TruffleOutputGroup o = !isPrintGraphEnabled() ? null
                                        : TruffleOutputGroup.open(debug, callTarget, Collections.singletonMap(GROUP_ID, compilation))) {
                            // Create "AST" and "Call Tree" groups if dumping is enabled.
                            maybeDumpTruffleTree(debug, callTarget);
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
                    // used by language-agnostic inlining
                    inlining.dequeueTargets();
                } finally {
                    if (initialDebug == null) {
                        debug.close();
                    }
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
            callTarget.onCompilationFailed(() -> CompilableTruffleAST.serializeException(t), false, false, false);
        }
    }

    @SuppressWarnings("try")
    private static void maybeDumpTruffleTree(TruffleDebugContext debug, OptimizedCallTarget callTarget) throws Exception {
        try (AutoCloseable c = debug.scope("TruffleTree")) {
            if (debug.isDumpEnabled()) {
                TruffleTreeDumper.dump(debug, callTarget);
            }
        }
    }

    public abstract BackgroundCompileQueue getCompileQueue();

    @SuppressWarnings("try")
    public CompilationTask submitForCompilation(OptimizedCallTarget optimizedCallTarget, boolean lastTierCompilation) {
        Priority priority = new Priority(optimizedCallTarget.getCallAndLoopCount(), lastTierCompilation ? Priority.Tier.LAST : Priority.Tier.FIRST);
        return getCompileQueue().submitCompilation(priority, optimizedCallTarget);

    }

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    public void finishCompilation(OptimizedCallTarget optimizedCallTarget, CompilationTask task, boolean mayBeAsynchronous) {

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

    private static void uninterruptibleWaitForCompilation(CompilationTask task) throws ExecutionException {
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
        CompilationTask task = optimizedCallTarget.getCompilationTask();
        if (task != null) {
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
        return selectObjectLayoutFactory(loadService(LayoutFactory.class));
    }

    private static <T> List<ServiceLoader<T>> loadService(Class<T> service) {
        ServiceLoader<T> graalLoader = ServiceLoader.load(service, GraalTruffleRuntime.class.getClassLoader());
        if (Java8OrEarlier) {
            return Collections.singletonList(graalLoader);
        } else {
            /*
             * The Graal module (i.e., jdk.internal.vm.compiler) is loaded by the platform class
             * loader on JDK 9+. Its module dependencies such as Truffle are supplied via
             * --module-path which means they are loaded by the app class loader. As such, we need
             * to search the app class loader path as well.
             */
            ServiceLoader<T> appLoader = ServiceLoader.load(service, service.getClassLoader());
            return Arrays.asList(graalLoader, appLoader);
        }
    }

    private static LayoutFactory selectObjectLayoutFactory(Iterable<? extends Iterable<LayoutFactory>> availableLayoutFactories) {
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
        } else if (frameSlotKindTag == FrameSlotKind.Byte.tag) {
            return JavaKind.Byte;
        } else if (frameSlotKindTag == FrameSlotKind.Int.tag) {
            return JavaKind.Int;
        } else if (frameSlotKindTag == FrameSlotKind.Float.tag) {
            return JavaKind.Float;
        } else if (frameSlotKindTag == FrameSlotKind.Long.tag) {
            return JavaKind.Long;
        } else if (frameSlotKindTag == FrameSlotKind.Double.tag) {
            return JavaKind.Double;
        } else if (frameSlotKindTag == FrameSlotKind.Object.tag) {
            return JavaKind.Object;
        } else if (frameSlotKindTag == FrameSlotKind.Illegal.tag) {
            return JavaKind.Illegal;
        }
        return JavaKind.Illegal;
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
        return FrameSlotKind.Illegal.tag;
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
        } else if (JFRListener.isInstrumented(original)) {
            return InlineKind.DO_NOT_INLINE_WITH_EXCEPTION;
        }
        return InlineKind.INLINE;
    }

    @Override
    public boolean isTruffleBoundary(ResolvedJavaMethod method) {
        return getAnnotation(TruffleBoundary.class, method) != null;
    }

    @Override
    public boolean isSpecializationMethod(ResolvedJavaMethod method) {
        return getAnnotation(Specialization.class, method) != null;
    }

    @Override
    public boolean isBytecodeInterpreterSwitch(ResolvedJavaMethod method) {
        return getAnnotation(BytecodeInterpreterSwitch.class, method) != null;
    }

    @Override
    public boolean isBytecodeInterpreterSwitchBoundary(ResolvedJavaMethod method) {
        return getAnnotation(BytecodeInterpreterSwitchBoundary.class, method) != null;
    }

    @Override
    public void log(String loggerId, CompilableTruffleAST compilable, String message) {
        ((OptimizedCallTarget) compilable).engine.getLogger(loggerId).log(Level.INFO, message);
    }

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

    final OptionDescriptors getEngineOptionDescriptors() {
        return engineOptions;
    }

    /**
     * Returns OptimizedCallTarget's {@link PolyglotCompilerOptions} as a {@link Map}. The returned
     * map can be passed as a {@code options} to the {@link TruffleCompiler} methods.
     */
    public static Map<String, Object> getOptionsForCompiler(OptimizedCallTarget callTarget) {
        Map<String, Object> map = new HashMap<>();
        OptionValues values = callTarget == null ? null : callTarget.getOptionValues();

        for (OptionDescriptor desc : PolyglotCompilerOptions.getDescriptors()) {
            final OptionKey<?> key = desc.getKey();
            if (values.hasBeenSet(key)) {
                Object value = values.get(key);
                if (!isPrimitiveType(value)) {
                    value = GraalRuntimeAccessor.ENGINE.getUnparsedOptionValue(values, key);
                }
                if (value != null) {
                    map.put(desc.getName(), value);
                }
            }
        }
        return map;
    }

    private static boolean isPrimitiveType(Object value) {
        Class<?> valueClass = value.getClass();
        return valueClass == Boolean.class ||
                        valueClass == Byte.class ||
                        valueClass == Short.class ||
                        valueClass == Character.class ||
                        valueClass == Integer.class ||
                        valueClass == Long.class ||
                        valueClass == Float.class ||
                        valueClass == Double.class ||
                        valueClass == String.class;
    }
}
