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
package org.graalvm.compiler.truffle.runtime;

import static org.graalvm.compiler.truffle.common.TruffleOutputGroup.GROUP_ID;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilerIdleDelay;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.nio.Buffer;
import java.nio.file.FileSystems;
import java.nio.file.Path;
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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.OptimizationFailedException;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.AbstractAssumption;
import com.oracle.truffle.api.impl.AbstractFastThreadLocal;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
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
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;

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

    private static final int JAVA_SPECIFICATION_VERSION = Runtime.version().feature();

    /**
     * Used only to reset state for native image compilation.
     */
    protected void clearState() {
        assert TruffleOptions.AOT : "Must be called only in AOT mode.";
        knownMethods = null;
    }

    private final GraalTruffleRuntimeListenerDispatcher listeners = new GraalTruffleRuntimeListenerDispatcher();

    protected volatile TruffleCompiler truffleCompiler;

    protected KnownMethods knownMethods;

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
    private final FloodControlHandler floodControlHandler;

    public GraalTruffleRuntime(Iterable<Class<?>> extraLookupTypes) {
        this.lookupTypes = initLookupTypes(extraLookupTypes);
        List<OptionDescriptors> options = new ArrayList<>();
        this.loopNodeFactory = loadGraalRuntimeServiceProvider(LoopNodeFactory.class, options, true);
        EngineCacheSupport support = loadGraalRuntimeServiceProvider(EngineCacheSupport.class, options, false);
        this.engineCacheSupport = support == null ? new EngineCacheSupport.Disabled() : support;
        options.add(PolyglotCompilerOptions.getDescriptors());
        this.engineOptions = OptionDescriptors.createUnion(options.toArray(new OptionDescriptors[options.size()]));
        this.floodControlHandler = loadGraalRuntimeServiceProvider(FloodControlHandler.class, null, false);
    }

    public boolean isLatestJVMCI() {
        return true;
    }

    public abstract ThreadLocalHandshake getThreadLocalHandshake();

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
     * Returns a set of classes that need to be initialized before compilations can be performed.
     */
    public final Iterable<Class<?>> getLookupTypes() {
        return lookupTypes.getValues();
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
        if (field.isAnnotationPresent(Child.class)) {
            return TruffleCompilerRuntime.ConstantFieldInfo.CHILD;
        }
        if (field.isAnnotationPresent(Children.class)) {
            return TruffleCompilerRuntime.ConstantFieldInfo.CHILDREN;
        }
        CompilationFinal cf = field.getAnnotation(CompilationFinal.class);
        if (cf != null) {
            int dimensions = actualStableDimensions(field, cf.dimensions());
            return TruffleCompilerRuntime.ConstantFieldInfo.forDimensions(dimensions);
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

    @SuppressWarnings("deprecation")
    private static UnmodifiableEconomicMap<String, Class<?>> initLookupTypes(Iterable<Class<?>> extraTypes) {
        EconomicMap<String, Class<?>> m = EconomicMap.create();
        for (Class<?> c : new Class<?>[]{
                        Node.class,
                        RootNode.class,
                        UnexpectedResultException.class,
                        SlowPathException.class,
                        OptimizedCallTarget.class,
                        OptimizedDirectCallNode.class,
                        OptimizedAssumption.class,
                        CompilerDirectives.class,
                        InlineDecision.class,
                        CompilerAsserts.class,
                        ExactMath.class,
                        ArrayUtils.class,
                        FrameDescriptor.class,
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
                        TruffleSafepoint.class,
                        BaseOSRRootNode.class,
                        TruffleString.class,
                        AbstractTruffleString.class,
                        Buffer.class,
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
        if (JAVA_SPECIFICATION_VERSION >= 16 && JAVA_SPECIFICATION_VERSION < 19) {
            String className = "jdk.internal.access.foreign.MemorySegmentProxy";
            try {
                Class<?> c = Class.forName(className);
                m.put(c.getName(), c);
            } catch (ClassNotFoundException e) {
                throw new NoClassDefFoundError(className);
            }
        } else if (JAVA_SPECIFICATION_VERSION >= 19) {
            String className = "jdk.internal.foreign.Scoped";
            try {
                Class<?> c = Class.forName(className);
                m.put(c.getName(), c);
            } catch (ClassNotFoundException e) {
                throw new NoClassDefFoundError(className);
            }
        }
        for (String className : new String[]{
                        "com.oracle.truffle.api.strings.TStringOps",
                        "com.oracle.truffle.object.UnsafeAccess",
        }) {
            try {
                Class<?> c = Class.forName(className);
                m.put(c.getName(), c);
            } catch (ClassNotFoundException e) {
                throw new NoClassDefFoundError(className);
            }
        }
        return m;
    }

    /*
     * Make sure the libgraal version HSTruffleCompilerRuntime.resolveType of this method stays in
     * sync with this method.
     */
    @Override
    public ResolvedJavaType resolveType(MetaAccessProvider metaAccess, String className, boolean required) {
        Class<?> c = lookupTypes.get(className);
        if (c == null) {
            if (!required) {
                return null;
            }
            throw new NoClassDefFoundError(className);
        }
        ResolvedJavaType type = metaAccess.lookupJavaType(c);
        // In some situations, we may need the class to be linked now, especially if we are
        // compiling immediately (e.g., to successfully devirtualize FrameWithoutBoxing methods).
        type.link();
        return type;
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

    public final void initializeKnownMethods(MetaAccessProvider metaAccess) {
        knownMethods = new KnownMethods(metaAccess);
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
    public final DirectCallNode createDirectCallNode(CallTarget target) {
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
    public final IndirectCallNode createIndirectCallNode() {
        return new OptimizedIndirectCallNode();
    }

    @Override
    public final VirtualFrame createVirtualFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return OptimizedCallTarget.createFrame(frameDescriptor, arguments);
    }

    @Override
    public final MaterializedFrame createMaterializedFrame(Object[] arguments) {
        return createMaterializedFrame(arguments, new FrameDescriptor());
    }

    @Override
    public final MaterializedFrame createMaterializedFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return new FrameWithoutBoxing(frameDescriptor, arguments);
    }

    @Override
    public final Assumption createAssumption() {
        return createAssumption(null);
    }

    @Override
    public final Assumption createAssumption(String name) {
        return new OptimizedAssumption(name);
    }

    public final GraalTruffleRuntimeListener getListener() {
        return listeners;
    }

    @TruffleBoundary
    @Override
    public final <T> T iterateFrames(FrameInstanceVisitor<T> visitor, int skipFrames) {
        if (skipFrames < 0) {
            throw new IllegalArgumentException("The skipFrames parameter must be >= 0.");
        }
        return iterateImpl(visitor, skipFrames);
    }

    /**
     * The compilation threshold scale is a real number. We use an integer which we treat as a fixed
     * point value for performance reasons.
     */
    private int compilationThresholdScale = FixedPointMath.toFixedPoint(1.0);

    public final int compilationThresholdScale() {
        return compilationThresholdScale;
    }

    final void setCompilationThresholdScale(int scale) {
        this.compilationThresholdScale = scale;
    }

    /**
     * This class visits native frames in order to construct Truffle {@link FrameInstance
     * FrameInstances}, which it passes to the provided {@link FrameInstanceVisitor}.
     */
    private static final class FrameVisitor<T> implements InspectedFrameVisitor<T> {

        private final FrameInstanceVisitor<T> visitor;
        private final KnownMethods methods;

        private int skipFrames;

        private InspectedFrame callNodeFrame;
        private InspectedFrame osrFrame;

        FrameVisitor(FrameInstanceVisitor<T> visitor, KnownMethods methods, int skip) {
            this.visitor = visitor;
            this.methods = methods;
            this.skipFrames = skip;
        }

        /**
         * A Truffle {@link FrameInstance} logically consists of three components: a
         * {@link com.oracle.truffle.api.frame.Frame frame}, a {@link CallTarget call target}, and a
         * {@link Node call node}. These objects are spread across multiple native
         * {@link InspectedFrame InspectedFrames}, so this visitor remembers previously-seen native
         * frames (as necessary) in order to construct Truffle {@link FrameInstance FrameInstances}.
         *
         * For example, consider this sample stack trace:
         *
         * <pre>
         *  ... -> executeRootNode(A) -> callDirect -> executeRootNode(B) -> callDirect -> executeRootNode(C)
         *        (call target, frame)  (call node)   (call target, frame)  (call node)   (call target, frame)
         *                |__________________|                |__________________|                |
         *                  FrameInstance(A)                    FrameInstance(B)           FrameInstance(C)
         * </pre>
         *
         * Method C is at the top of the stack (it has not called another guest method). Thus, it
         * does not have a call node. Its call target and frame are the first two parameters of
         * executeRootNode(C) {@link InspectedFrame}, so we can construct a {@link FrameInstance}
         * using this frame.
         *
         * Down the stack, method B calls C, so it does have a call node. This node is a parameter
         * to the callDirect {@link InspectedFrame}, so we remember this frame and use it to
         * construct a {@link FrameInstance} when we reach executeRootNode(B). We construct a
         * {@link FrameInstance} for A the same way.
         *
         *
         * OSR complicates things. Consider this sample stack trace:
         *
         * <pre>
         *  ... -> executeRootNode(A) -> callOSR -> executeRootNode(A_OSR) -> callDirect -> ...
         *      (non-OSR call target, _)               (_, new frame)         (call node)
         *               |___________________________________|____________________|
         *                                    FrameInstance(A)
         * </pre>
         *
         * With OSR, the program state may be inconsistent between OSR and non-OSR frames. The OSR
         * frame (executeRootNode(A_OSR)) contains the most up-to-date Truffle
         * {@link com.oracle.truffle.api.frame.Frame}, so we remember it. OSR should be transparent,
         * so the call target is obtained from the non-OSR frame (executeRootNode(A)).
         */
        @Override
        public T visitFrame(InspectedFrame frame) {
            if (frame.isMethod(methods.callDirectMethod) || frame.isMethod(methods.callIndirectMethod) || frame.isMethod(methods.callInlinedMethod) ||
                            frame.isMethod(methods.callInlinedCallMethod)) {
                callNodeFrame = frame;
                return null;
            }
            assert frame.isMethod(methods.callTargetMethod);
            if (isOSRFrame(frame)) {
                if (skipFrames == 0 && osrFrame == null) {
                    osrFrame = frame;
                }
                return null;
            } else if (skipFrames > 0) {
                skipFrames--;
                return null;
            } else {
                try {
                    if (osrFrame != null) {
                        return visitor.visitFrame(new GraalOSRFrameInstance(frame, callNodeFrame, osrFrame));
                    } else {
                        return visitor.visitFrame(new GraalFrameInstance(frame, callNodeFrame));
                    }
                } finally {
                    osrFrame = null;
                    callNodeFrame = null;
                }
            }
        }

        private static boolean isOSRFrame(InspectedFrame frame) {
            return ((OptimizedCallTarget) frame.getLocal(GraalFrameInstance.CALL_TARGET_INDEX)).getRootNode() instanceof BaseOSRRootNode;
        }
    }

    private <T> T iterateImpl(FrameInstanceVisitor<T> visitor, final int skip) {
        KnownMethods methods = getKnownMethods();
        FrameVisitor<T> jvmciVisitor = new FrameVisitor<>(visitor, methods, skip);
        return getStackIntrospection().iterateFrames(methods.anyFrameMethod, methods.anyFrameMethod, 0, jvmciVisitor);
    }

    protected abstract StackIntrospection getStackIntrospection();

    @SuppressWarnings("deprecation")
    @Override
    public <T> T getCapability(Class<T> capability) {
        if (capability == TVMCI.class) {
            return capability.cast(tvmci);
        } else if (capability == com.oracle.truffle.api.object.LayoutFactory.class) {
            com.oracle.truffle.api.object.LayoutFactory layoutFactory = loadObjectLayoutFactory();
            ModuleUtil.exportTo(layoutFactory.getClass());
            return capability.cast(layoutFactory);
        } else if (capability == TVMCI.Test.class) {
            return capability.cast(getTestTvmci());
        }
        try {
            return loadServiceProvider(capability, false);
        } catch (ServiceConfigurationError e) {
            // Happens when a service type has not been exported to Graal
            // or Graal's module descriptor does not declare a use of capability.
            return null;
        }
    }

    public abstract SpeculationLog createSpeculationLog();

    protected abstract OptimizedCallTarget createOptimizedCallTarget(OptimizedCallTarget source, RootNode rootNode);

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
        Objects.requireNonNull(callTarget, "Cannot compile null call target.");
        Objects.requireNonNull(task, "Compilation task required.");
        List<OptimizedCallTarget> oldBlockCompilations = callTarget.blockCompilations;
        if (oldBlockCompilations != null) {
            for (OptimizedCallTarget blockTarget : oldBlockCompilations) {
                if (blockTarget.isValid()) {
                    continue;
                }
                listeners.onCompilationQueued(blockTarget, task.tier());
                int nodeCount = blockTarget.getNonTrivialNodeCount();
                if (nodeCount > callTarget.engine.getEngineOptions().get(PolyglotCompilerOptions.PartialBlockMaximumSize)) {
                    listeners.onCompilationDequeued(blockTarget, null, "Partial block is too big to be compiled.", task.tier());
                    continue;
                }
                compileImpl(debug, blockTarget, task);
            }
        }
        compileImpl(debug, callTarget, task);

        if (oldBlockCompilations == null && callTarget.blockCompilations != null) {
            // retry with block compilations
            ((CompilationTask) task).reset();
            listeners.onCompilationQueued(callTarget, task.tier());
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
                listeners.onCompilationStarted(callTarget, task);
                compilationStarted = true;
                try {
                    compiler.doCompile(debug, compilation, optionsMap, task, listeners.isEmpty() ? null : listeners);
                } finally {
                    if (initialDebug == null) {
                        debug.close();
                    }
                }
                TruffleInlining inlining = (TruffleInlining) task.inliningData();
                truffleDump(callTarget, compiler, compilation, optionsMap, inlining);
                inlining.dequeueTargets();
            }
        } catch (OptimizationFailedException e) {
            // Listeners already notified
            throw e;
        } catch (RuntimeException | Error e) {
            notifyCompilationFailure(callTarget, e, compilationStarted, task.tier());
            throw e;
        } catch (Throwable e) {
            notifyCompilationFailure(callTarget, e, compilationStarted, task.tier());
            throw new InternalError(e);
        }
    }

    @SuppressWarnings("try")
    private void truffleDump(OptimizedCallTarget callTarget, TruffleCompiler compiler, TruffleCompilation compilation, Map<String, Object> optionsMap, TruffleInlining inlining) throws Exception {
        try (TruffleDebugContext debug = compiler.openDebugContext(optionsMap, compilation)) {
            try (AutoCloseable s = debug.scope("Truffle", new TruffleDebugJavaMethod(callTarget));
                            TruffleOutputGroup o = isPrintGraphEnabled() ? TruffleOutputGroup.openCallTarget(debug, callTarget, Collections.singletonMap(GROUP_ID, compilation)) : null) {
                if (!debug.isDumpEnabled()) {
                    return;
                }
                if (inlining.inlinedTargets().length > 1) {
                    TruffleTreeDumper.dump(debug, callTarget, inlining);
                }
                TruffleTreeDumper.dump(debug, callTarget);
            }
        }
    }

    private void notifyCompilationFailure(OptimizedCallTarget callTarget, Throwable t, boolean compilationStarted, int tier) {
        try {
            if (compilationStarted) {
                listeners.onCompilationFailed(callTarget, t.toString(), false, false, tier);
            } else {
                listeners.onCompilationDequeued(callTarget, this, String.format("Failed to create Truffle compiler due to %s.", t.getMessage()), tier);
            }
        } finally {
            Supplier<String> serializedException = () -> CompilableTruffleAST.serializeException(t);
            callTarget.onCompilationFailed(serializedException, isSuppressedTruffleRuntimeException(t) || isSuppressedFailure(callTarget, serializedException), false, false, false);
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
        BackgroundCompileQueue compileQueue = getCompileQueue();
        return compileQueue == null ? 0 : compileQueue.getQueueSize();
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

    public KnownMethods getKnownMethods() {
        return knownMethods;
    }

    /**
     * Use {@link OptimizedCallTarget#engine} whenever possible as it's much faster.
     */
    protected static EngineData getEngineData(RootNode rootNode) {
        return GraalTVMCI.getEngineData(rootNode);
    }

    @SuppressWarnings("deprecation")
    private static com.oracle.truffle.api.object.LayoutFactory loadObjectLayoutFactory() {
        return selectObjectLayoutFactory(loadService(com.oracle.truffle.api.object.LayoutFactory.class));
    }

    private static <T> List<ServiceLoader<T>> loadService(Class<T> service) {
        ServiceLoader<T> graalLoader = ServiceLoader.load(service, GraalTruffleRuntime.class.getClassLoader());
        /*
         * The Graal module (i.e., jdk.internal.vm.compiler) is loaded by the platform class loader.
         * Its module dependencies such as Truffle are supplied via --module-path which means they
         * are loaded by the app class loader. As such, we need to search the app class loader path
         * as well.
         */
        ServiceLoader<T> appLoader = ServiceLoader.load(service, service.getClassLoader());
        return Arrays.asList(graalLoader, appLoader);
    }

    @SuppressWarnings("deprecation")
    private static com.oracle.truffle.api.object.LayoutFactory selectObjectLayoutFactory(Iterable<? extends Iterable<com.oracle.truffle.api.object.LayoutFactory>> availableLayoutFactories) {
        String layoutFactoryImplName = Services.getSavedProperties().get("truffle.object.LayoutFactory");
        com.oracle.truffle.api.object.LayoutFactory bestLayoutFactory = null;
        for (Iterable<com.oracle.truffle.api.object.LayoutFactory> currentLayoutFactories : availableLayoutFactories) {
            for (com.oracle.truffle.api.object.LayoutFactory currentLayoutFactory : currentLayoutFactories) {
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

    public final class KnownMethods {
        public final ResolvedJavaMethod callDirectMethod;
        public final ResolvedJavaMethod callInlinedMethod;
        public final ResolvedJavaMethod callIndirectMethod;
        public final ResolvedJavaMethod callTargetMethod;
        public final ResolvedJavaMethod callInlinedCallMethod;
        public final ResolvedJavaMethod[] anyFrameMethod;
        public final ResolvedJavaMethod inInterpreterMethod;
        public final ResolvedJavaMethod[] transferToInterpreterMethods;

        public KnownMethods(MetaAccessProvider metaAccess) {
            this.callDirectMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_DIRECT);
            this.callIndirectMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_INDIRECT);
            this.callInlinedMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_INLINED);
            this.callInlinedCallMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_INLINED_CALL);
            this.callTargetMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_TARGET_METHOD);
            this.anyFrameMethod = new ResolvedJavaMethod[]{callDirectMethod, callIndirectMethod, callInlinedMethod, callTargetMethod, callInlinedCallMethod};
            ResolvedJavaType compilerDirectives = metaAccess.lookupJavaType(CompilerDirectives.class);
            this.transferToInterpreterMethods = new ResolvedJavaMethod[2];
            this.transferToInterpreterMethods[0] = searchMethod(compilerDirectives, "transferToInterpreter");
            this.transferToInterpreterMethods[1] = searchMethod(compilerDirectives, "transferToInterpreterAndInvalidate");
            this.inInterpreterMethod = searchMethod(compilerDirectives, "inInterpreter");
        }
    }

    protected static ResolvedJavaMethod searchMethod(ResolvedJavaType type, String name) {
        for (ResolvedJavaMethod searchMethod : type.getDeclaredMethods()) {
            if (searchMethod.getName().equals(name)) {
                return searchMethod;
            }
        }
        throw CompilerDirectives.shouldNotReachHere(type + "." + name + " method not found.");
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
    public boolean isInlineable(ResolvedJavaMethod method) {
        /*
         * Ensure that methods excluded from inlining are also never inlined during Truffle
         * compilation.
         */
        return method.canBeInlined();
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
    public boolean isInliningCutoff(ResolvedJavaMethod method) {
        return getAnnotation(InliningCutoff.class, method) != null;
    }

    /**
     * Determines if {@code method} is an inInterpeter method.
     */
    @Override
    public boolean isInInterpreter(ResolvedJavaMethod targetMethod) {
        return getKnownMethods().inInterpreterMethod.equals(targetMethod);
    }

    /**
     * Determines if {@code method} is a method is a transferToInterpreter method.
     */
    @Override
    public boolean isTransferToInterpreterMethod(ResolvedJavaMethod method) {
        ResolvedJavaMethod[] methods = getKnownMethods().transferToInterpreterMethods;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].equals(method)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isBytecodeInterpreterSwitchBoundary(ResolvedJavaMethod method) {
        return getAnnotation(com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitchBoundary.class, method) != null;
    }

    @Override
    public void log(String loggerId, CompilableTruffleAST compilable, String message) {
        ((OptimizedCallTarget) compilable).engine.getLogger(loggerId).log(Level.INFO, message);
    }

    @Override
    public boolean isSuppressedFailure(CompilableTruffleAST compilable, Supplier<String> serializedException) {
        return floodControlHandler != null && floodControlHandler.isSuppressedFailure(compilable, serializedException);
    }

    /**
     * Allows {@link GraalTruffleRuntime} subclasses to suppress exceptions such as an exception
     * thrown during VM exit. Unlike {@link #isSuppressedFailure(CompilableTruffleAST, Supplier)}
     * this method is called only for exceptions thrown on the Truffle runtime side, so it does not
     * need to stringify the passed exception.
     */
    @SuppressWarnings("unused")
    protected boolean isSuppressedTruffleRuntimeException(Throwable throwable) {
        return false;
    }

    // https://bugs.openjdk.java.net/browse/JDK-8209535

    private static BailoutException handleAnnotationFailure(NoClassDefFoundError e, String attemptedAction) {
        throw new BailoutException(e, "Error while %s. " +
                        "This usually means that the unresolved type is in the signature of some other " +
                        "method or field in the same class. This can be resolved by modifying the relevant class path " +
                        "or module path such that it includes the missing type.",
                        attemptedAction);
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
     * Gets the time in milliseconds an idle compiler thread will wait for new tasks before
     * terminating. A value of {@code <= 0} means that compiler threads block indefinitely waiting
     * for a task and thus never terminate.
     */
    protected long getCompilerIdleDelay(OptimizedCallTarget callTarget) {
        OptionValues options = callTarget.engine.getEngineOptions();
        /*
         * Compiler idle time is set to 0 to avoid that the compiler thread is shut down. We are
         * collecting statistics if any of the flags are used.
         */
        if (!options.get(PolyglotCompilerOptions.MethodExpansionStatistics).isEmpty() || !options.get(PolyglotCompilerOptions.NodeExpansionStatistics).isEmpty() ||
                        options.get(PolyglotCompilerOptions.InstrumentBranches) || options.get(PolyglotCompilerOptions.InstrumentBoundaries)) {
            return 0L;
        }
        return callTarget.getOptionValue(CompilerIdleDelay);
    }

    final OptionDescriptors getEngineOptionDescriptors() {
        return engineOptions;
    }

    /**
     * Returns OptimizedCallTarget's {@link PolyglotCompilerOptions} as a {@link Map}. The returned
     * map can be passed as a {@code options} to the {@link TruffleCompiler} methods.
     */
    public static Map<String, Object> getOptionsForCompiler(OptimizedCallTarget target) {
        Map<String, Object> map = new HashMap<>();
        OptionValues values = target.engine.getEngineOptions();

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

    protected int getObjectAlignment() {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    protected int getArrayBaseOffset(Class<?> componentType) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    protected int getArrayIndexScale(Class<?> componentType) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    protected int getBaseInstanceSize(Class<?> type) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    protected Object[] getResolvedFields(Class<?> type, boolean includePrimitive, boolean includeSuperclasses) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    protected Object getFieldValue(ResolvedJavaField resolvedJavaField, Object obj) {
        throw new UnsupportedOperationException();
    }

    protected abstract AbstractFastThreadLocal getFastThreadLocalImpl();

    public long getStackOverflowLimit() {
        throw new UnsupportedOperationException();
    }

    public static class StackTraceHelper {
        public static void logHostAndGuestStacktrace(String reason, OptimizedCallTarget callTarget) {
            final int limit = callTarget.getOptionValue(PolyglotCompilerOptions.TraceStackTraceLimit);
            final GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
            final StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(reason).append(" at\n");
            runtime.iterateFrames(new FrameInstanceVisitor<Object>() {
                int frameIndex = 0;

                @Override
                public Object visitFrame(FrameInstance frameInstance) {
                    CallTarget target = frameInstance.getCallTarget();
                    StringBuilder line = new StringBuilder("  ");
                    if (frameIndex > 0) {
                        line.append("  ");
                    }
                    line.append(formatStackFrame(frameInstance, target)).append("\n");
                    frameIndex++;

                    messageBuilder.append(line);
                    if (frameIndex < limit) {
                        return null;
                    } else {
                        messageBuilder.append("    ...\n");
                        return frameInstance;
                    }
                }

            });
            final int skip = 3;
            StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            String suffix = stackTrace.length > skip + limit ? "\n    ..." : "";
            messageBuilder.append(Arrays.stream(stackTrace).skip(skip).limit(limit).map(StackTraceElement::toString).collect(Collectors.joining("\n    ", "  ", suffix)));
            runtime.log(callTarget, messageBuilder.toString());
        }

        private static String formatStackFrame(FrameInstance frameInstance, CallTarget target) {
            StringBuilder builder = new StringBuilder();
            if (target instanceof RootCallTarget) {
                RootNode root = ((RootCallTarget) target).getRootNode();
                String name = root.getName();
                if (name == null) {
                    builder.append("unnamed-root");
                } else {
                    builder.append(name);
                }
                Node callNode = frameInstance.getCallNode();
                SourceSection sourceSection = null;
                if (callNode != null) {
                    sourceSection = callNode.getEncapsulatingSourceSection();
                }
                if (sourceSection == null) {
                    sourceSection = root.getSourceSection();
                }

                if (sourceSection == null || sourceSection.getSource() == null) {
                    builder.append("(Unknown)");
                } else {
                    builder.append("(").append(formatPath(sourceSection)).append(":").append(sourceSection.getStartLine()).append(")");
                }

                if (target instanceof OptimizedCallTarget) {
                    OptimizedCallTarget callTarget = ((OptimizedCallTarget) target);
                    if (callTarget.isSplit()) {
                        builder.append(" <split-").append(Integer.toHexString(callTarget.hashCode())).append(">");
                    }
                }

            } else {
                builder.append(target.toString());
            }
            return builder.toString();
        }

        private static String formatPath(SourceSection sourceSection) {
            if (sourceSection.getSource().getPath() != null) {
                Path path = FileSystems.getDefault().getPath(".").toAbsolutePath();
                Path filePath = FileSystems.getDefault().getPath(sourceSection.getSource().getPath()).toAbsolutePath();

                try {
                    return path.relativize(filePath).toString();
                } catch (IllegalArgumentException e) {
                    // relativization failed
                }
            }
            return sourceSection.getSource().getName();
        }
    }
}
