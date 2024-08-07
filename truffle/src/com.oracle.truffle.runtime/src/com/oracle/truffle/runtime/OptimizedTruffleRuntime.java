/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.CompilerIdleDelay;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.home.Version;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.OptimizationFailedException;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.InlineSupport;
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
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.compiler.ConstantFieldInfo;
import com.oracle.truffle.compiler.HostMethodInfo;
import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.PartialEvaluationMethodInfo;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationSupport;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;
import com.oracle.truffle.runtime.BackgroundCompileQueue.Priority;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions.ExceptionAction;
import com.oracle.truffle.runtime.debug.JFRListener;
import com.oracle.truffle.runtime.debug.StatisticsListener;
import com.oracle.truffle.runtime.debug.TraceASTCompilationListener;
import com.oracle.truffle.runtime.debug.TraceCompilationListener;
import com.oracle.truffle.runtime.debug.TraceCompilationPolymorphismListener;
import com.oracle.truffle.runtime.debug.TraceSplittingListener;
import com.oracle.truffle.runtime.serviceprovider.TruffleRuntimeServices;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.stack.InspectedFrame;
import jdk.vm.ci.code.stack.InspectedFrameVisitor;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.meta.JavaConstant;
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

public abstract class OptimizedTruffleRuntime implements TruffleRuntime, TruffleCompilerRuntime {

    private static final int JAVA_SPECIFICATION_VERSION = Runtime.version().feature();
    public static final Version MIN_COMPILER_VERSION = Version.create(23, 1, 2);
    public static final int MIN_JDK_VERSION = 21;
    public static final int MAX_JDK_VERSION = 25;
    public static final Version NEXT_VERSION_UPDATE = Version.create(25, 1);

    /**
     * Used only to reset state for native image compilation.
     */
    protected void clearState() {
        assert TruffleOptions.AOT : "Must be called only in AOT mode.";
        this.knownMethods = null;
    }

    private final OptimizedTruffleRuntimeListenerDispatcher listeners = new OptimizedTruffleRuntimeListenerDispatcher();

    protected volatile TruffleCompiler truffleCompiler;
    protected volatile OptimizedCallTarget initializeCallTarget;

    protected KnownMethods knownMethods;

    private final OptimizedTVMCI tvmci = new OptimizedTVMCI();
    private volatile OptimizedTestTVMCI testTvmci;

    /**
     * Utility method that casts the singleton {@link TruffleRuntime}.
     */
    public static OptimizedTruffleRuntime getRuntime() {
        return (OptimizedTruffleRuntime) Truffle.getRuntime();
    }

    private final LoopNodeFactory loopNodeFactory;
    private EngineCacheSupport engineCacheSupport;
    private final UnmodifiableEconomicMap<String, Class<?>> lookupTypes;
    private final FloodControlHandler floodControlHandler;
    private final List<OptionDescriptors> runtimeOptionDescriptors;
    protected volatile OptionDescriptors engineOptions;

    protected final TruffleCompilationSupport compilationSupport;
    private OptionDescriptors previousEngineCacheSupportOptions;

    @SuppressWarnings("this-escape")
    public OptimizedTruffleRuntime(TruffleCompilationSupport compilationSupport, Iterable<Class<?>> extraLookupTypes) {
        this.compilationSupport = compilationSupport;
        this.lookupTypes = initLookupTypes(extraLookupTypes);
        List<OptionDescriptors> options = new ArrayList<>();
        this.loopNodeFactory = loadGraalRuntimeServiceProvider(LoopNodeFactory.class, options, true);
        EngineCacheSupport support = loadEngineCacheSupport(options);
        this.engineCacheSupport = support == null ? new EngineCacheSupport.Disabled() : support;
        this.previousEngineCacheSupportOptions = engineCacheSupport.getEngineOptions();
        options.add(OptimizedRuntimeOptions.getDescriptors());
        options.add(new CompilerOptionsDescriptors());
        this.runtimeOptionDescriptors = options;
        this.floodControlHandler = loadGraalRuntimeServiceProvider(FloodControlHandler.class, null, false);
    }

    public final void initializeEngineCacheSupport(EngineCacheSupport support) {
        this.runtimeOptionDescriptors.remove(this.previousEngineCacheSupportOptions);
        this.engineCacheSupport = support;
        OptionDescriptors engineCacheOptions = support.getEngineOptions();
        this.previousEngineCacheSupportOptions = engineCacheOptions;
        this.runtimeOptionDescriptors.add(engineCacheOptions);
    }

    protected EngineCacheSupport loadEngineCacheSupport(List<OptionDescriptors> options) {
        return loadGraalRuntimeServiceProvider(EngineCacheSupport.class, options, false);
    }

    public boolean isLatestJVMCI() {
        return true;
    }

    public abstract ThreadLocalHandshake getThreadLocalHandshake();

    @Override
    public String getName() {
        String compilerConfigurationName = String.valueOf(getCompilerConfigurationName());
        return switch (compilerConfigurationName) {
            case "community" -> "GraalVM CE";
            case "enterprise" -> "Oracle GraalVM";
            default -> {
                assert false : "unexpected compiler configuration name: " + compilerConfigurationName;
                yield "GraalVM " + compilerConfigurationName;
            }
        };
    }

    /**
     * Returns a set of classes that need to be initialized before compilations can be performed.
     */
    public final Iterable<Class<?>> getLookupTypes() {
        return lookupTypes.getValues();
    }

    /**
     * This method allows retrieval of the compiler configuration without requiring to initialize
     * the {@link TruffleCompiler} with {@link #getTruffleCompiler(TruffleCompilable)
     * getTruffleCompiler}.
     */
    public final String getCompilerConfigurationName() {
        return compilationSupport.getCompilerConfigurationName(this);
    }

    public abstract TruffleCompiler getTruffleCompiler(TruffleCompilable compilable);

    public final TruffleCompilerOptionDescriptor[] listCompilerOptions() {
        return compilationSupport.listCompilerOptions();
    }

    public final boolean existsCompilerOption(String key) {
        return compilationSupport.compilerOptionExists(key);
    }

    public final String validateCompilerOption(String key, String value) {
        return compilationSupport.validateCompilerOption(key, value);
    }

    protected OptimizedTVMCI getTvmci() {
        return tvmci;
    }

    protected TVMCI.Test<?, ?> getTestTvmci() {
        if (testTvmci == null) {
            synchronized (this) {
                if (testTvmci == null) {
                    testTvmci = new OptimizedTestTVMCI();
                }
            }
        }
        return testTvmci;
    }

    @Override
    public TruffleCompilable asCompilableTruffleAST(JavaConstant constant) {
        return asObject(OptimizedCallTarget.class, constant);
    }

    @Override
    public Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumptionConstant) {
        final OptimizedAssumption optimizedAssumption = asObject(OptimizedAssumption.class, optimizedAssumptionConstant);
        return optimizedAssumption.registerDependency();
    }

    protected abstract JavaConstant forObject(Object object);

    protected abstract <T> T asObject(Class<T> type, JavaConstant constant);

    public final TruffleCompiler newTruffleCompiler() {
        return compilationSupport.createCompiler(this);
    }

    private static <T> T loadServiceProvider(Class<T> clazz, boolean failIfNotFound) {
        Iterable<T> providers;
        if (ImageInfo.inImageBuildtimeCode()) {
            providers = ServiceLoader.load(clazz);
        } else {
            providers = TruffleRuntimeServices.load(clazz);
        }
        boolean priorityService = OptimizedRuntimeServiceProvider.class.isAssignableFrom(clazz);
        T bestFactory = null;
        int bestPriority = 0;
        for (T factory : providers) {
            int currentPriority;
            if (priorityService) {
                currentPriority = ((OptimizedRuntimeServiceProvider) factory).getPriority();
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

    private static <T extends OptimizedRuntimeServiceProvider> T loadGraalRuntimeServiceProvider(Class<T> clazz, List<OptionDescriptors> descriptors, boolean failIfNotFound) {
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
            return ConstantFieldInfo.CHILD;
        }
        if (field.isAnnotationPresent(Children.class)) {
            return ConstantFieldInfo.CHILDREN;
        }
        CompilationFinal cf = field.getAnnotation(CompilationFinal.class);
        if (cf != null) {
            int dimensions = actualStableDimensions(field, cf.dimensions());
            return ConstantFieldInfo.forDimensions(dimensions);
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
                        HostCompilerDirectives.class,
                        CompilerDirectives.class,
                        InlineDecision.class,
                        CompilerAsserts.class,
                        ExactMath.class,
                        ArrayUtils.class,
                        FrameDescriptor.class,
                        FrameSlotKind.class,
                        MethodHandle.class,
                        ArrayList.class,
                        AbstractAssumption.class,
                        VirtualFrame.class,
                        MaterializedFrame.class,
                        CompilationState.class,
                        FrameWithoutBoxing.class,
                        BranchProfile.class,
                        ConditionProfile.class,
                        Objects.class,
                        TruffleSafepoint.class,
                        BaseOSRRootNode.class,
                        TruffleString.class,
                        AbstractTruffleString.class,
                        AssertionError.class,
                        Buffer.class,
                        Shape.class,
                        InstalledCode.class,
                        DynamicObject.class,
                        InlineSupport.InlinableField.class,
                        InlineSupport.StateField.class,
                        InlineSupport.BooleanField.class,
                        InlineSupport.ByteField.class,
                        InlineSupport.ShortField.class,
                        InlineSupport.IntField.class,
                        InlineSupport.CharField.class,
                        InlineSupport.FloatField.class,
                        InlineSupport.LongField.class,
                        InlineSupport.DoubleField.class,
                        InlineSupport.ReferenceField.class,
        }) {
            m.put(c.getName(), c);
        }

        // initialize values
        FrameSlotKind.values();

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
            for (String className : new String[]{
                            "jdk.internal.misc.ScopedMemoryAccess$ScopedAccessError",
                            "jdk.internal.foreign.AbstractMemorySegmentImpl",
            }) {
                try {
                    Class<?> c = Class.forName(className);
                    m.put(c.getName(), c);
                } catch (ClassNotFoundException e) {
                    throw new NoClassDefFoundError(className);
                }
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
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        } catch (IllegalStateException e) {
            // shutdown already in progress
            // catching the exception is the only way to detect this.
        }
    }

    @Override
    public HostMethodInfo getHostMethodInfo(ResolvedJavaMethod method) {
        return new HostMethodInfo(isTruffleBoundary(method),
                        isBytecodeInterpreterSwitch(method),
                        isBytecodeInterpreterSwitchBoundary(method),
                        isInliningCutoff(method));
    }

    private static boolean isBytecodeInterpreterSwitch(ResolvedJavaMethod method) {
        return getAnnotation(BytecodeInterpreterSwitch.class, method) != null;
    }

    private static boolean isInliningCutoff(ResolvedJavaMethod method) {
        return getAnnotation(InliningCutoff.class, method) != null;
    }

    @SuppressWarnings("deprecation")
    private static boolean isBytecodeInterpreterSwitchBoundary(ResolvedJavaMethod method) {
        return getAnnotation(com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitchBoundary.class, method) != null;
    }

    private static boolean isTruffleBoundary(ResolvedJavaMethod method) {
        return getAnnotation(TruffleBoundary.class, method) != null;
    }

    @Override
    public PartialEvaluationMethodInfo getPartialEvaluationMethodInfo(ResolvedJavaMethod method) {
        TruffleBoundary truffleBoundary = getAnnotation(TruffleBoundary.class, method);
        TruffleCallBoundary truffleCallBoundary = getAnnotation(TruffleCallBoundary.class, method);
        return new PartialEvaluationMethodInfo(getLoopExplosionKind(method),
                        getInlineKind(truffleBoundary, truffleCallBoundary, method, true),
                        getInlineKind(truffleBoundary, truffleCallBoundary, method, false),
                        method.canBeInlined(),
                        isSpecializationMethod(method));
    }

    private static boolean isSpecializationMethod(ResolvedJavaMethod method) {
        return getAnnotation(Specialization.class, method) != null;
    }

    private static LoopExplosionKind getLoopExplosionKind(ResolvedJavaMethod method) {
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

    private static InlineKind getInlineKind(TruffleBoundary truffleBoundary, TruffleCallBoundary truffleCallBoundary, ResolvedJavaMethod method, boolean duringPartialEvaluation) {
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
        } else if (truffleCallBoundary != null) {
            return InlineKind.DO_NOT_INLINE_WITH_EXCEPTION;
        } else if (JFRListener.isInstrumented(method)) {
            return InlineKind.DO_NOT_INLINE_WITH_EXCEPTION;
        }
        return InlineKind.INLINE;
    }

    public final void initializeKnownMethods(MetaAccessProvider metaAccess) {
        knownMethods = new KnownMethods(metaAccess);
    }

    /** Accessor for non-public state in {@link FrameDescriptor}. */
    public void markFrameMaterializeCalled(FrameDescriptor descriptor) {
        OptimizedRuntimeAccessor.FRAME.markMaterializeCalled(descriptor);
    }

    /** Accessor for non-public state in {@link FrameDescriptor}. */
    public boolean getFrameMaterializeCalled(FrameDescriptor descriptor) {
        return OptimizedRuntimeAccessor.FRAME.getMaterializeCalled(descriptor);
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

    public final OptimizedTruffleRuntimeListener getListener() {
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
                        return visitor.visitFrame(new OptimizedOSRFrameInstance(frame, callNodeFrame, osrFrame));
                    } else {
                        return visitor.visitFrame(new OptimizedFrameInstance(frame, callNodeFrame));
                    }
                } finally {
                    osrFrame = null;
                    callNodeFrame = null;
                }
            }
        }

        private static boolean isOSRFrame(InspectedFrame frame) {
            return ((OptimizedCallTarget) frame.getLocal(OptimizedFrameInstance.CALL_TARGET_INDEX)).getRootNode() instanceof BaseOSRRootNode;
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

    protected abstract OptimizedCallTarget createInitializationCallTarget(EngineData engine);

    public void addListener(OptimizedTruffleRuntimeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(OptimizedTruffleRuntimeListener listener) {
        listeners.remove(listener);
    }

    private void shutdown() {
        getListener().onShutdown();
        TruffleCompiler tcp = truffleCompiler;
        if (tcp != null) {
            tcp.shutdown();
        }
    }

    protected final void doCompile(OptimizedCallTarget callTarget, AbstractCompilationTask task) {
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
                if (nodeCount > callTarget.engine.getEngineOptions().get(OptimizedRuntimeOptions.PartialBlockMaximumSize)) {
                    listeners.onCompilationDequeued(blockTarget, null, "Partial block is too big to be compiled.", task.tier());
                    continue;
                }
                compileImpl(blockTarget, task);
            }
        }
        compileImpl(callTarget, task);

        if (oldBlockCompilations == null && callTarget.blockCompilations != null) {
            // retry with block compilations
            ((CompilationTask) task).reset();
            listeners.onCompilationQueued(callTarget, task.tier());
            doCompile(callTarget, task);
        }
    }

    @SuppressWarnings("try")
    private void compileImpl(OptimizedCallTarget callTarget, AbstractCompilationTask task) {
        boolean compilationStarted = false;
        try {
            TruffleCompiler compiler = getTruffleCompiler(callTarget);
            listeners.onCompilationStarted(callTarget, task);
            compilationStarted = true;
            compiler.doCompile(task, callTarget, listeners.isEmpty() ? null : listeners);
            task.dequeueTargets();
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

    private void notifyCompilationFailure(OptimizedCallTarget callTarget, Throwable t, boolean compilationStarted, int tier) {
        try {
            if (compilationStarted) {
                listeners.onCompilationFailed(callTarget, t.toString(), false, false, tier, () -> TruffleCompilable.serializeException(t));
            } else {
                listeners.onCompilationDequeued(callTarget, this, String.format("Failed to create Truffle compiler due to %s.", t.getMessage()), tier);
            }
        } finally {
            Supplier<String> serializedException = () -> TruffleCompilable.serializeException(t);
            callTarget.onCompilationFailed(serializedException, isSuppressedCompilationFailure(t) || isSuppressedFailure(callTarget, serializedException), false, false, false);
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
                    throw new OptimizationFailedException(e.getCause(), optimizedCallTarget);
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
        return OptimizedTVMCI.getEngineData(rootNode);
    }

    @SuppressWarnings("deprecation")
    private static com.oracle.truffle.api.object.LayoutFactory loadObjectLayoutFactory() {
        return selectObjectLayoutFactory(loadService(com.oracle.truffle.api.object.LayoutFactory.class));
    }

    private static <T> List<ServiceLoader<T>> loadService(Class<T> service) {
        ClassLoader runtimeClassLoader = OptimizedTruffleRuntime.class.getClassLoader();
        ClassLoader appClassLoader = service.getClassLoader();
        ServiceLoader<T> appLoader = ServiceLoader.load(service, appClassLoader);
        if (runtimeClassLoader.equals(appClassLoader)) {
            /*
             * Primary mode of operation for Truffle consumed from the application module path.
             */
            return List.of(appLoader);
        } else {
            ServiceLoader<T> runtimeLoader = ServiceLoader.load(service, runtimeClassLoader);
            /*
             * The Graal module (i.e., jdk.internal.vm.compiler) is loaded by the platform class
             * loader. Its module dependencies such as Truffle are supplied via --module-path which
             * means they are loaded by the app class loader. As such, we need to search the app
             * class loader path as well.
             */
            return List.of(runtimeLoader, appLoader);
        }
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

        public KnownMethods(MetaAccessProvider metaAccess) {
            this.callDirectMethod = metaAccess.lookupJavaMethod(OptimizedFrameInstance.CALL_DIRECT);
            this.callIndirectMethod = metaAccess.lookupJavaMethod(OptimizedFrameInstance.CALL_INDIRECT);
            this.callInlinedMethod = metaAccess.lookupJavaMethod(OptimizedFrameInstance.CALL_INLINED);
            this.callInlinedCallMethod = metaAccess.lookupJavaMethod(OptimizedFrameInstance.CALL_INLINED_CALL);
            this.callTargetMethod = metaAccess.lookupJavaMethod(OptimizedFrameInstance.CALL_TARGET_METHOD);
            this.anyFrameMethod = new ResolvedJavaMethod[]{callDirectMethod, callIndirectMethod, callInlinedMethod, callTargetMethod, callInlinedCallMethod};
        }
    }

    @Override
    public boolean isValueType(ResolvedJavaType type) {
        return getAnnotation(CompilerDirectives.ValueType.class, type) != null;
    }

    @Override
    public void log(String loggerId, TruffleCompilable compilable, String message) {
        TruffleLogger logger = ((OptimizedCallTarget) compilable).engine.getLogger(loggerId);
        // The logger can be null if the engine is closed.
        if (logger != null) {
            logger.log(Level.INFO, message);
        }
    }

    @Override
    public boolean isSuppressedFailure(TruffleCompilable compilable, Supplier<String> serializedException) {
        return floodControlHandler != null && floodControlHandler.isSuppressedFailure(compilable, serializedException);
    }

    /**
     * Allows {@link OptimizedTruffleRuntime} subclasses to suppress exceptions such as an exception
     * thrown during VM exit. Unlike {@link #isSuppressedFailure(TruffleCompilable, Supplier)} this
     * method is called only for exceptions thrown on the Truffle runtime side, so it does not need
     * to stringify the passed exception.
     */
    @SuppressWarnings("unused")
    final boolean isSuppressedCompilationFailure(Throwable throwable) {
        return compilationSupport.isSuppressedCompilationFailure(throwable);
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
    protected final AutoCloseable openCompilerThreadScope() {
        return compilationSupport.openCompilerThreadScope();
    }

    /**
     * Gets the time in milliseconds an idle compiler thread will wait for new tasks before
     * terminating. A value of {@code <= 0} means that compiler threads block indefinitely waiting
     * for a task and thus never terminate.
     */
    protected long getCompilerIdleDelay(OptimizedCallTarget callTarget) {
        return callTarget.getOptionValue(CompilerIdleDelay);
    }

    final OptionDescriptors getOptionDescriptors() {
        // The engineOptions field needs to be initialized lazily because the
        // OptimizedRuntimeAccessor
        // cannot be used in the OptimizedTruffleRuntime constructor. The OptimizedTruffleRuntime
        // must be
        // fully initialized before using the accessor otherwise a NullPointerException will be
        // thrown from the Accessor.Constants static initializer because the Truffle#getRuntime
        // still returns null.
        OptionDescriptors res = engineOptions;
        if (res == null) {
            res = OptimizedRuntimeAccessor.LANGUAGE.createOptionDescriptorsUnion(runtimeOptionDescriptors.toArray(new OptionDescriptors[runtimeOptionDescriptors.size()]));
            engineOptions = res;
        }
        return res;
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
    protected int[] getFieldOffsets(Class<?> type, boolean includePrimitive, boolean includeSuperclasses) {
        throw new UnsupportedOperationException();
    }

    protected abstract AbstractFastThreadLocal getFastThreadLocalImpl();

    public long getStackOverflowLimit() {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    public static <T> ThreadLocal<T> createTerminatingThreadLocal(Supplier<T> initialValue, Consumer<T> onThreadTermination) {
        try {
            Method m = Services.class.getMethod("createTerminatingThreadLocal", Supplier.class, Consumer.class);
            return (ThreadLocal<T>) m.invoke(null, initialValue, onThreadTermination);
        } catch (NoSuchMethodException e) {
            return ThreadLocal.withInitial(initialValue);
        } catch (ReflectiveOperationException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    public static class StackTraceHelper {
        public static void logHostAndGuestStacktrace(String reason, OptimizedCallTarget callTarget) {
            final int limit = callTarget.getOptionValue(OptimizedRuntimeOptions.TraceStackTraceLimit);
            final OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
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

    static final class CompilerOptionsDescriptors implements OptionDescriptors {

        /*
         * We do not need real keys for compiler options as they are never read on the runtime side.
         */

        TruffleCompilerOptionDescriptor[] options;

        /*
         * Used to validate options by name. e.g. for validation requests.
         */
        private static final Map<String, OptionKey<String>> KEYS = new ConcurrentHashMap<>();

        /*
         * Used to extract option values from OptionValues. Since we do not have a generated
         * descriptor option on the runtime side we enumerate all options that were used globally.
         */
        private static final Map<OptionKey<String>, String> NAMES = new ConcurrentHashMap<>();

        CompilerOptionsDescriptors() {
        }

        @Override
        public OptionDescriptor get(String optionName) {
            String newOptionName = null;
            if (optionName.startsWith("compiler.")) {
                newOptionName = optionName;
            } else if (isLegacyOption(optionName)) {
                newOptionName = convertFromLegacyOptionName(optionName);
            }

            if (newOptionName != null && OptimizedTruffleRuntime.getRuntime().existsCompilerOption(newOptionName)) {
                OptionDescriptor.Builder b = OptionDescriptor.newBuilder(getOrCreateOptionKey(optionName), optionName);
                if (isLegacyOption(optionName)) {
                    b.deprecated(true).deprecationMessage(
                                    String.format("The option %s is now deprecated. Please use the new option name '%s' instead to resolve this.", optionName, newOptionName));
                }
                return b.build();
            }

            return null;
        }

        static OptionKey<String> getOrCreateOptionKey(String name) {
            return KEYS.computeIfAbsent(name, (k) -> {
                OptionType<String> type = new OptionType<>("compilerOption", (s) -> s, (v) -> {
                    String optionName = name;
                    if (isLegacyOption(optionName)) {
                        optionName = convertFromLegacyOptionName(optionName);
                    }
                    String result = OptimizedTruffleRuntime.getRuntime().validateCompilerOption(optionName, v);
                    if (result != null) {
                        throw new IllegalArgumentException(result);
                    }
                });
                var key = new OptionKey<>("", type);
                NAMES.put(key, name);
                return key;
            });

        }

        static boolean isLegacyOption(String optionName) {
            switch (optionName) {
                case "engine.EncodedGraphCache":
                case "engine.ExcludeAssertions":
                case "engine.FirstTierInliningPolicy":
                case "engine.FirstTierUseEconomy":
                case "engine.InlineAcrossTruffleBoundary":
                case "engine.InlineOnly":
                case "engine.Inlining":
                case "engine.InliningExpansionBudget":
                case "engine.InliningInliningBudget":
                case "engine.InliningPolicy":
                case "engine.InliningRecursionDepth":
                case "engine.InliningUseSize":
                case "engine.InstrumentBoundaries":
                case "engine.InstrumentBoundariesPerInlineSite":
                case "engine.InstrumentBranches":
                case "engine.InstrumentBranchesPerInlineSite":
                case "engine.InstrumentFilter":
                case "engine.InstrumentationTableSize":
                case "engine.IterativePartialEscape":
                case "engine.MaximumGraalGraphSize":
                case "engine.MethodExpansionStatistics":
                case "engine.NodeExpansionStatistics":
                case "engine.NodeSourcePositions":
                case "engine.ParsePEGraphsWithAssumptions":
                case "engine.TraceInlining":
                case "engine.TraceInliningDetails":
                case "engine.TraceMethodExpansion":
                case "engine.TraceNodeExpansion":
                case "engine.TracePerformanceWarnings":
                case "engine.TraceStackTraceLimit":
                case "engine.TreatPerformanceWarningsAsErrors":
                    return true;
            }
            return false;
        }

        static String convertFromLegacyOptionName(String optionName) {
            return optionName.replaceFirst("engine", "compiler");
        }

        static String convertToLegacyOptionName(String optionName) {
            return optionName.replaceFirst("compiler", "engine");
        }

        @Override
        public Iterator<OptionDescriptor> iterator() {
            TruffleCompilerOptionDescriptor[] optionsArray = this.options;
            if (optionsArray == null) {
                /*
                 * Compiler options descriptor never change so it is save to cache them per runtime.
                 */
                options = optionsArray = OptimizedTruffleRuntime.getRuntime().listCompilerOptions();
            }
            List<OptionDescriptor> descriptors = new ArrayList<>();
            for (TruffleCompilerOptionDescriptor descriptor : optionsArray) {
                descriptors.add(convertDescriptor(descriptor));
            }
            for (TruffleCompilerOptionDescriptor descriptor : optionsArray) {
                descriptors.add(convertDescriptorLegacy(descriptor));
            }
            return descriptors.iterator();
        }

        static OptionDescriptor convertDescriptorLegacy(TruffleCompilerOptionDescriptor d) {
            String name = convertToLegacyOptionName(d.name());
            return OptionDescriptor.newBuilder(getOrCreateOptionKey(name), name).help(d.help()).//
                            stability(OptionStability.EXPERIMENTAL).//
                            category(matchCategory(d)).//
                            deprecated(true).//
                            deprecationMessage(String.format("The option %s is now deprecated. Please use the new option name '%s' instead to resolve this.", name, d.name())).build();
        }

        static OptionDescriptor convertDescriptor(TruffleCompilerOptionDescriptor d) {
            String name = d.name();
            return OptionDescriptor.newBuilder(getOrCreateOptionKey(name), name).//
                            help(d.help()).//
                            stability(OptionStability.EXPERIMENTAL).//
                            category(matchCategory(d)).//
                            deprecated(d.deprecated()).//
                            deprecationMessage(d.deprecationMessage()).build();
        }

        static Map<String, String> extractOptions(OptionValues values) {
            Map<String, String> options = null;
            for (var entry : NAMES.entrySet()) {
                if (options == null) {
                    options = new LinkedHashMap<>();
                }
                String optionName = entry.getValue();
                if (!values.hasBeenSet(entry.getKey())) {
                    continue;
                }
                String optionValue = values.get(entry.getKey());
                if (isLegacyOption(optionName)) {
                    optionName = convertFromLegacyOptionName(optionName);
                }
                options.put(optionName, optionValue);
            }
            return options == null ? new LinkedHashMap<>() : options;
        }

        static OptionCategory matchCategory(TruffleCompilerOptionDescriptor d) {
            switch (d.type()) {
                case USER:
                    return OptionCategory.USER;
                case EXPERT:
                    return OptionCategory.EXPERT;
                case DEBUG:
                    return OptionCategory.INTERNAL;
                default:
                    return OptionCategory.INTERNAL;
            }
        }
    }

}
