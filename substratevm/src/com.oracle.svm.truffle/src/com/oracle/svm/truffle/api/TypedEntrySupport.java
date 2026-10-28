/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import static com.oracle.svm.shared.util.SubstrateUtil.toUnboxedClassWithDefault;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.TraceCompilation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.impl.Word;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.guest.staging.c.function.FunctionPointerHolder;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.auximage.AuxiliaryImageCodeObserver;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.code.ImageCodeInfoProvider;
import com.oracle.svm.jvmci.shared.meta.DeoptStub.StubType;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.jvmci.shared.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;
import com.oracle.svm.core.graal.isolated.ClientHandle;
import com.oracle.svm.core.graal.isolated.ClientIsolateThread;
import com.oracle.svm.core.graal.isolated.CompilerHandle;
import com.oracle.svm.core.graal.isolated.CompilerIsolateThread;
import com.oracle.svm.core.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.core.graal.isolated.IsolatedCompileContext;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.graal.nodes.ComputedIndirectCallTargetNode;
import com.oracle.svm.core.graal.nodes.ComputedIndirectCallTargetNode.Computation;
import com.oracle.svm.core.graal.nodes.ComputedIndirectCallTargetNode.FieldLoad;
import com.oracle.svm.core.graal.nodes.ComputedIndirectCallTargetNode.FieldLoadIfZero;
import com.oracle.svm.core.graal.replacements.SubstrateGraphKit;
import com.oracle.svm.core.meta.MethodRefSupport;
import com.oracle.svm.jvmci.shared.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.shared.ReflectivelyAccessed;
import com.oracle.svm.graal.RuntimeCompilationSupport;
import com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompilationFeature;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.isolated.ImageHeapObjects;
import com.oracle.svm.graal.isolated.ImageHeapRef;
import com.oracle.svm.graal.isolated.IsolatedCompilationMethod;
import com.oracle.svm.graal.isolated.IsolatedGraalUtils;
import com.oracle.svm.graal.isolated.IsolatedHandles;
import com.oracle.svm.graal.isolated.IsolatedObjectConstant;
import com.oracle.svm.graal.isolated.IsolatedRuntimeCodeInstaller;
import com.oracle.svm.graal.meta.RuntimeCodeInstaller;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.graal.meta.SubstrateMetaAccess;
import com.oracle.svm.graal.meta.SubstrateSignature;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions;
import com.oracle.svm.guest.staging.core.heap.UnknownPrimitiveField;
import com.oracle.svm.guest.staging.option.RuntimeOptionValues;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.shared.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.OriginalMethodProvider;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.runtime.OptimizedAssumption;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedCallTarget.ArgumentsProfile;
import com.oracle.truffle.runtime.OptimizedCallTarget.ReturnProfile;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.RetryableBailoutException;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup.SpeculationContextObject;
import jdk.graal.compiler.truffle.PartialEvaluationMethodInfo;
import jdk.graal.compiler.truffle.nodes.TruffleAssumption;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.annotation.AbstractAnnotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

/**
 * Support for an unboxed calling convention for direct calls between Truffle functions. Truffle
 * interpreters pass arguments boxed as an Object[] array, which in addition requires boxing of
 * primitive arguments. Optimizing the calling convention for compiled Truffle functions leads to
 * different kinds of calls. The caller and the callee can independently be interpreted (I) or
 * compiled (C):
 *
 * <ul>
 * <li>C2C direct calls: {@link DirectCallNode Direct truffle calls} from compiled code to compiled
 * code. This is the case that we optimize for. Arguments are passed unboxed (in registers / stack
 * slots as determined by the underlying Java calling convention), and the return value is unboxed.
 * Profiling information and assumptions are used to invalidate the optimized code in case types are
 * changing in the application.</li>
 *
 * <li>C2C indirect calls: {@link IndirectCallNode Indirect truffle calls} from compiled code to
 * compiled code. The unboxed calling convention is not feasible because it is not known which
 * Truffle function is called, i.e., it would be necessary to check the profiling information and
 * assumptions in compiled code.</li>
 *
 * <li>I2C calls: Calls from the interpreter to compiled code need an I2C adapter that unboxes the
 * Object[] array of the interpreter and puts the unboxed arguments in the correct locations.</li>
 *
 * <li>C2I calls: Calls from compiled code to the interpreter need an C2I adapter that collects the
 * unboxed arguments and allocates an Object[] array for the interpreter.</li>
 *
 * <li>I2I calls: Calls from the interpreter to the interpreter do not need any special handling,
 * both caller and callee work with the boxed Object[] array.</li>
 * </ul>
 *
 * C2I and I2C adapters are created on demand, and are shared between Truffle functions with the
 * same typed signature. Adapters are compiled by Graal using hand-crafted Graal graphs (created in
 * {@link #createC2IAdapterGraph} and {@link #createI2CAdapterGraph}).
 *
 * The unboxed signature is injected into the root method used for partial evaluation by wrapping
 * the normal root method in a {@link TypedEntryMethod}. The partial evaluation root methods are the
 * different {@link SubstrateOptimizedCallTarget#compiledEntryReturnObject
 * compiledEntryReturnObject*} methods. They all first call
 * {@link SubstrateOptimizedCallTarget#specializeArguments} that reconstructs the
 * arguments Object[] array from the unboxed parameters. So the first high-level Graal graph after
 * parsing has {@link ParameterNode}s for the unboxed signature, a {@link NewArrayNode}, and
 * {@link StoreIndexedNode}s to fill the arguments Object[] array. Escape analysis virtualizes the
 * array later on, which then leads to the desired Graal graph that does not use an arguments array
 * at all. If the compiled code gets deoptimized, the arguments array is re-allocated during
 * deoptimization.
 *
 * Return values are also unboxed. However, it is not possible to change out the return type in the
 * {@link TypedEntryMethod} because deoptimization would then change the return type. In other
 * words, we need the compiled code and the interpreter Java method to have the same return type.
 * Therefore, every supported unboxed return type needs a separate compilation root method
 * {@link SubstrateOptimizedCallTarget#compiledEntryReturnObject
 * compiledEntryReturnObject*}. When a return type specialization fails, then, e.g., a method with
 * the return type int needs to return an object. We wrap the object in a
 * {@link DeoptimizedResultException}. Note that a failed return type specialization leads to both
 * the deoptimization of the callee and the caller, i.e., throwing and catching the
 * {@link DeoptimizedResultException} is never in compiled code.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public final class TypedEntrySupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void beforeAnalysis(BeforeAnalysisAccessImpl access) {
        TypedEntrySupport support = new TypedEntrySupport(access.getMetaAccess());
        ImageSingletons.add(TypedEntrySupport.class, support);
        support.initializeBeforeAnalysis(access);
        support.registerInterpreterEntryMethodsAsCompiled(access);
        Method[] entries = ((SubstrateTruffleRuntime) Truffle.getRuntime()).getExactGeneratedProxyEntries();
        if (entries.length != 0) {
            /* The fallback constant may only become reachable during AOT compilation. */
            access.registerAsInHeap(FunctionPointerHolder.class, "generated proxy entry fallback");
            for (Method entryMethod : entries) {
                AnalysisMethod entry = access.getMetaAccess().lookupJavaMethod(entryMethod);
                support.generatedEntryFallbacks.put(entryMethod, new FunctionPointerHolder(MethodRefSupport.createMethodPointer(entry)));
                access.registerAsRoot(entry, true, "generated proxy entry fallback");
            }
        }
        RuntimeCompilationFeature runtimeCompilationFeature = RuntimeCompilationFeature.singleton();
        for (ResolvedJavaMethod method : support.compiledEntryMethods.values()) {
            runtimeCompilationFeature.prepareMethodForRuntimeCompilation(method, access);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void beforeCompilation(Feature.BeforeCompilationAccess access) {
        TypedEntrySupport support = singleton();
        support.initializeBeforeCompilation(access);
        access.registerAsImmutable(support);
    }

    static TypedEntrySupport singleton() {
        return Lazy.singleton();
    }

    /*
     * @Fold annotated methods are verified when hosted assertions are enabled. However
     * TypedEntrySupport is not always linkable, for example if the Truffle runtime is not on the
     * module.path. So to avoid failing we put the singleton lookup method into a separate class.
     */
    static class Lazy {
        @Fold
        static TypedEntrySupport singleton() {
            return ImageSingletons.lookup(TypedEntrySupport.class);
        }
    }

    public static class Options {
        @Option(help = "Use typed unboxed arguments for Truffle compilations", type = OptionType.Debug)//
        public static final HostedOptionKey<Boolean> UseTypedArguments = new HostedOptionKey<>(true);

        @Option(help = "Use typed unboxed return value for Truffle compilations", type = OptionType.Debug)//
        public static final HostedOptionKey<Boolean> UseTypedResult = new HostedOptionKey<>(true);
    }

    /**
     * All signatures contain one additional parameter: the
     * {@link SubstrateOptimizedCallTarget}. For simplicity, we only use static methods in
     * the Java code where the "receiver" call target is explicitly passed as the first parameter.
     */
    private static final int PREFIX_PARAMETER_COUNT = 1;

    /**
     * The primitive types for which return type specialization is supported. Adding a type here
     * also requires additions in many places in {@link SubstrateOptimizedCallTarget}.
     */
    private static final EnumSet<JavaKind> TYPED_RETURN_KINDS = EnumSet.of(JavaKind.Boolean, JavaKind.Byte, JavaKind.Short, JavaKind.Char, JavaKind.Int, JavaKind.Long,
                    JavaKind.Float, JavaKind.Double, JavaKind.Object);

    private ResolvedJavaField fromCompiledSpecializedEntryPointField;
    private ResolvedJavaField fromCompiledUnspecializedEntryPointField;
    private ResolvedJavaField compiledIndirectEntryAddressField;
    private ResolvedJavaField generatedEntryFallbackAddressField;

    private final Map<JavaKind, ResolvedJavaMethod> compiledEntryMethods;
    private final Map<JavaKind, ResolvedJavaMethod> interpreterEntryMethods;

    @UnknownPrimitiveField(availability = ReadyForCompilation.class) //
    @ReflectivelyAccessed //
    private CFunctionPointer compiledIndirectEntryAddress;

    private final ConcurrentMap<String, SubstrateInstalledCode> adapters = new ConcurrentHashMap<>();
    private final ConcurrentMap<Method, FunctionPointerHolder> generatedEntryFallbacks = new ConcurrentHashMap<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    TypedEntrySupport(MetaAccessProvider metaAccess) {
        compiledEntryMethods = new EnumMap<>(JavaKind.class);
        interpreterEntryMethods = new EnumMap<>(JavaKind.class);
        for (JavaKind returnKind : TYPED_RETURN_KINDS) {
            compiledEntryMethods.put(returnKind, metaAccess.lookupJavaMethod(compiledEntryMethod(returnKind)));
            interpreterEntryMethods.put(returnKind, metaAccess.lookupJavaMethod(interpreterEntryMethod(returnKind)));
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static Method compiledEntryMethod(JavaKind returnKind) {
        try {
            return SubstrateOptimizedCallTarget.class.getDeclaredMethod("compiledEntryReturn" + returnKind.name(), SubstrateOptimizedCallTarget.class, Object[].class);
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static Method interpreterEntryMethod(JavaKind returnKind) {
        try {
            return SubstrateOptimizedCallTarget.class.getDeclaredMethod("interpreterEntryReturn" + returnKind.name(), SubstrateOptimizedCallTarget.class, Object[].class);
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static Method compiledIndirectEntryMethod() {
        try {
            return SubstrateOptimizedCallTarget.class.getDeclaredMethod("compiledIndirectEntryReturnObject", SubstrateOptimizedCallTarget.class, Object[].class);
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void registerInterpreterEntryMethodsAsCompiled(Feature.BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;

        for (ResolvedJavaMethod interpreterEntryMethod : interpreterEntryMethods.values()) {
            access.registerAsRoot((AnalysisMethod) interpreterEntryMethod, true, "Truffle interpreter entry methods, registered in " + TypedEntrySupport.class);
        }
        access.registerAsRoot(access.getMetaAccess().lookupJavaMethod(compiledIndirectEntryMethod()), true,
                        "Truffle compiled indirect entry methods, registered in " + TypedEntrySupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void initializeBeforeAnalysis(BeforeAnalysisAccessImpl access) {
        /* Fields accessed by the address computation of a ComputedIndirectCallTargetNode. */
        fromCompiledSpecializedEntryPointField = registerAsAccessed(access, SubstrateOptimizedCallTarget.class, "fromCompiledSpecializedEntryPoint");
        fromCompiledUnspecializedEntryPointField = registerAsAccessed(access, SubstrateOptimizedCallTarget.class, "fromCompiledUnspecializedEntryPoint");
        compiledIndirectEntryAddressField = registerAsAccessed(access, TypedEntrySupport.class, "compiledIndirectEntryAddress");
        generatedEntryFallbackAddressField = registerAsAccessed(access, FunctionPointerHolder.class, "functionPointer");
    }

    private static AnalysisField registerAsAccessed(BeforeAnalysisAccessImpl access, Class<?> declaringClass, String fieldName) {
        var field = access.getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(declaringClass, fieldName));
        access.registerAsAccessed(field, "is Truffle accessed field");
        return field;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void initializeBeforeCompilation(Feature.CompilationAccess a) {
        CompilationAccessImpl access = (CompilationAccessImpl) a;

        compiledIndirectEntryAddress = MethodRefSupport.createMethodPointer(access.getMetaAccess().lookupJavaMethod(compiledIndirectEntryMethod()));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerExactEntryPlugin(GraphBuilderConfiguration.Plugins plugins, MetaAccessProvider metaAccess, Method[] entries) {
        if (entries.length == 0) {
            return;
        }
        Map<ResolvedJavaMethod, Method> exactEntries = new HashMap<>();
        for (Method entry : entries) {
            exactEntries.put(OriginalMethodProvider.getOriginalMethod(metaAccess.lookupJavaMethod(entry)), entry);
        }
        /* Generated entries can exceed the fixed arities supported by InvocationPlugin.apply. */
        plugins.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode[] arguments) {
                Method entryMethod = exactEntries.get(OriginalMethodProvider.getOriginalMethod(targetMethod));
                if (entryMethod == null) {
                    return false;
                }
                TypedEntrySupport support = singleton();
                /*
                 * Select the compiled or AOT fallback address before issuing one indirect call.
                 */
                FunctionPointerHolder fallback = support.generatedEntryFallbacks.get(entryMethod);
                VMError.guarantee(fallback != null, "Generated proxy entry fallback was not registered for %s", entryMethod);
                assert targetMethod.isStatic();
                assert arguments.length >= 3;
                ValueNode callTargetNode = arguments[0];
                RuntimeConfiguration runtimeConfiguration = RuntimeCompilationSupport.getRuntimeConfig();
                JavaConstant fallbackConstant = runtimeConfiguration.getProviders().getSnippetReflection().forObject(fallback);
                Computation[] addressComputation = {
                                new FieldLoad(support.fromCompiledSpecializedEntryPointField),
                                new FieldLoadIfZero(fallbackConstant, support.generatedEntryFallbackAddressField)
                };
                JavaKind returnKind = targetMethod.getSignature().getReturnKind();
                StampPair returnStamp = StampFactory.forDeclaredType(b.getGraph().getAssumptions(), targetMethod.getSignature().getReturnType(targetMethod.getDeclaringClass()), false);
                CallTargetNode indirectCallTargetNode = b.add(new ComputedIndirectCallTargetNode(callTargetNode, addressComputation, arguments,
                                returnStamp, targetMethod.getSignature().toParameterTypes(null), targetMethod));
                /* Preserve the caller's exception handlers, including Java-default fallback. */
                b.handleReplacedInvoke(indirectCallTargetNode, returnKind);
                return true;
            }
        });
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static boolean supportsExactGeneratedProxyCall(Method method) {
        /* Generated roots prepend the language context and receiver. */
        if (!(Modifier.isAbstract(method.getModifiers()) || method.isDefault()) || !supportsTypedArgumentCount(method.getParameterCount() + 2)) {
            return false;
        }
        Class<?> returnType = method.getReturnType();
        return returnType == void.class || !returnType.isPrimitive() ||
                        Options.UseTypedResult.getValue() && TYPED_RETURN_KINDS.contains(JavaKind.fromJavaClass(returnType));
    }

    /*
     * Specialized signatures must remain below escape analysis's array virtualization limit. A
     * lower limit also avoids passing many arguments on the stack instead of in a heap array.
     * The count must be constant during partial evaluation so this check can constant fold.
     */
    static boolean supportsTypedArgumentCount(int count) {
        return Options.UseTypedArguments.getValue() && count < 20;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerRuntimePlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, SubstrateOptimizedCallTarget.class);
        for (JavaKind returnKind : TYPED_RETURN_KINDS) {
            r.register(new RequiredInvocationPlugin("invokeFromCompiledReturn" + returnKind.name(), SubstrateOptimizedCallTarget.class, Object[].class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode callTargetNode, ValueNode boxedParametersNode) {
                    if (canDelayIntrinsification) {
                        /* Do not intrinsify during native image generation. */
                        return false;
                    }
                    singleton().intrinsifyInvokeFromCompiled(b, targetMethod, callTargetNode, boxedParametersNode);
                    return true;
                }
            });
        }
        r.register(new RequiredInvocationPlugin("specializeArguments", SubstrateOptimizedCallTarget.class, Object[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode callTargetNode, ValueNode originalArgumentsNode) {
                if (canDelayIntrinsification) {
                    /* Do not intrinsify during native image generation. */
                    return false;
                }
                intrinsifySpecializeArguments(b, callTargetNode, originalArgumentsNode);
                return true;
            }
        });
    }

    ResolvedJavaMethod rootForCallTarget(TruffleCompilable ast) {
        if (isIsolatedCompilation()) {
            ClientHandle<?> callTargetHandle = ((IsolatedObjectConstant) ast.asJavaConstant()).getHandle();
            CompilerHandle<ResolvedJavaMethod> resultHandle = rootForIsolatedCallTarget(IsolatedCompileContext.get().getClient(), callTargetHandle);
            return IsolatedCompileContext.get().unhand(resultHandle);
        }
        return (ResolvedJavaMethod) typedEntryMethodForCallTarget(ast);
    }

    private Object typedEntryMethodForCallTarget(TruffleCompilable ast) {
        SubstrateOptimizedCallTarget callTarget = (SubstrateOptimizedCallTarget) ast;

        ArgumentsProfile argumentsProfile = callTarget.getArgumentsProfile();
        ReturnProfile returnProfile = callTarget.getReturnProfile();

        List<OptimizedAssumption> assumptionsList = new ArrayList<>();
        Class<?>[] argumentClasses = SubstrateOptimizedCallTarget.specializedArgumentClasses(argumentsProfile);
        if (argumentClasses != null) {
            assumptionsList.add(argumentsProfile.getAssumption());
        }

        Class<?> returnClass = getSpecializedReturnClass(argumentsProfile, returnProfile);
        if (returnClass != Object.class) {
            assumptionsList.add(returnProfile.getAssumption());
        }

        OptimizedAssumption[] assumptions = assumptionsList.toArray(new OptimizedAssumption[0]);

        boolean specialized = argumentClasses != null;
        SubstrateSignature signature = adaptSignature(argumentClasses, returnClass, specialized);
        CompilerHandle<SubstrateSignature> signatureInCompiler = IsolatedHandles.nullHandle();
        if (isIsolatedCompilation()) {
            signatureInCompiler = createAdaptedSignatureInCompiler(argumentClasses, returnClass, specialized);
        }

        SubstrateInstalledCode i2cAdapter = getOrCompileI2CAdapter(callTarget, signature, signatureInCompiler, specialized);

        String name = signatureToName(callTarget.getName(), signature, specialized);
        SharedRuntimeMethod originalEntryMethod = (SharedRuntimeMethod) compiledEntryMethods.get(signature.getReturnKind());
        JavaConstant[] assumptionConstants;
        if (isIsolatedCompilation()) {
            assumptionConstants = null; // only needed in compiler isolate (read below)
        } else {
            assumptionConstants = new JavaConstant[assumptions.length];
            for (int i = 0; i < assumptions.length; i++) {
                assumptionConstants[i] = SubstrateObjectConstant.forObject(assumptions[i]);
            }
        }
        TypedEntryMethod typedEntryMethod = new TypedEntryMethod(name, signature, assumptionConstants, specialized, originalEntryMethod, i2cAdapter);
        if (!isIsolatedCompilation()) {
            return typedEntryMethod;
        }

        /*
         * Isolated compilation: the TypedEntryMethod that we created above lives in the client
         * isolate. We also need to create a temporary TypedEntryMethod in the compiler isolate that
         * provides our assumptions, signature and possibly other information during compilation.
         * That object has a handle to the TypedEntryMethod in the client isolate that we use after
         * compilation to install the compiled code in the client isolate.
         */
        IsolatedCompileClient clientContext = IsolatedCompileClient.get();
        ClientHandle<?>[] assumptionHandles = new ClientHandle<?>[assumptions.length];
        for (int i = 0; i < assumptions.length; i++) {
            assumptionHandles[i] = clientContext.hand(assumptions[i]);
        }
        try (PinnedObject assumptionRefsPin = PinnedObject.create(assumptionHandles)) {
            CompilerHandle<ResolvedJavaMethod> compilerTypedEntryMethodHandle = createCompilationTypedEntryMethod(clientContext.getCompiler(),
                            clientContext.hand(typedEntryMethod), clientContext.createStringInCompiler(name), signatureInCompiler, assumptionRefsPin.addressOfArrayElement(0),
                            IsolatedGraalUtils.getNullableArrayLength(assumptionHandles), specialized, ImageHeapObjects.ref(originalEntryMethod));
            return compilerTypedEntryMethodHandle.rawValue();
        }
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<ResolvedJavaMethod> rootForIsolatedCallTarget(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<?> callTargetHandle) {
        TruffleCompilable callTarget = (TruffleCompilable) IsolatedCompileClient.get().unhand(callTargetHandle);
        long typedEntryMethodHandleValue = (long) singleton().typedEntryMethodForCallTarget(callTarget);
        return Word.signed(typedEntryMethodHandleValue);
    }

    private static CompilerHandle<SubstrateSignature> createAdaptedSignatureInCompiler(Class<?>[] argClasses, Class<?> returnClass, boolean specialized) {
        ImageHeapRef<?>[] argClassRefs = null;
        if (argClasses != null) {
            argClassRefs = new ImageHeapRef<?>[argClasses.length];
            for (int i = 0; i < argClasses.length; i++) {
                argClassRefs[i] = ImageHeapObjects.ref(argClasses[i]);
            }
        }
        try (PinnedObject argClassRefsPin = PinnedObject.create(argClassRefs)) {
            WordPointer argClassRefsElems = Word.nullPointer();
            if (argClassRefs != null) {
                argClassRefsElems = argClassRefsPin.addressOfArrayElement(0);
            }
            return createAdaptedSignatureInCompiler0(IsolatedCompileClient.get().getCompiler(), ImageHeapObjects.ref(returnClass),
                            argClassRefsElems, IsolatedGraalUtils.getNullableArrayLength(argClassRefs), specialized);
        }
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileContext.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileContext.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<SubstrateSignature> createAdaptedSignatureInCompiler0(@SuppressWarnings("unused") CompilerIsolateThread compiler,
                    ImageHeapRef<Class<?>> returnClassRef, WordPointer argClassRefsElems, int argClassesCount, boolean specialized) {

        Class<?> returnClass = ImageHeapObjects.deref(returnClassRef);
        Class<?>[] argClasses = null;
        if (argClassesCount >= 0) {
            argClasses = new Class<?>[argClassesCount];
            for (int i = 0; i < argClassesCount; i++) {
                argClasses[i] = ImageHeapObjects.deref(argClassRefsElems.read(i));
            }
        }
        SubstrateSignature signature = adaptSignature(argClasses, returnClass, specialized);
        return IsolatedCompileContext.get().hand(signature);
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileContext.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileContext.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<ResolvedJavaMethod> createCompilationTypedEntryMethod(@SuppressWarnings("unused") CompilerIsolateThread compiler,
                    ClientHandle<TypedEntryMethod> clientMethodHandle, CompilerHandle<String> nameHandle, CompilerHandle<SubstrateSignature> signatureHandle,
                    WordPointer assumptionHandlesElems, int assumptionsCount, boolean specialized, ImageHeapRef<SharedRuntimeMethod> originalEntryMethodRef) {

        String name = IsolatedCompileContext.get().unhand(nameHandle);
        SubstrateSignature signature = IsolatedCompileContext.get().unhand(signatureHandle);
        JavaConstant[] assumptionConstants = new JavaConstant[assumptionsCount];
        for (int i = 0; i < assumptionsCount; i++) {
            ClientHandle<?> assumptionHandle = assumptionHandlesElems.read(i);
            assumptionConstants[i] = new IsolatedObjectConstant(assumptionHandle, false);
        }
        SharedRuntimeMethod originalEntryMethod = ImageHeapObjects.deref(originalEntryMethodRef);
        ResolvedJavaMethod typedEntryMethod = new IsolatedTypedEntryMethod(clientMethodHandle, name, signature, assumptionConstants, specialized, originalEntryMethod);
        return IsolatedCompileContext.get().hand(typedEntryMethod);
    }

    private static Class<?> getSpecializedReturnClass(ArgumentsProfile argumentsProfile, ReturnProfile returnProfile) {
        return filterSpecializedReturnClass(SubstrateOptimizedCallTarget.specializedReturnClass(argumentsProfile, returnProfile));
    }

    static JavaKind getSpecializedReturnKind(ArgumentsProfile argumentsProfile, ReturnProfile returnProfile) {
        return JavaKind.fromJavaClass(toUnboxedClassWithDefault(getSpecializedReturnClass(argumentsProfile, returnProfile), Object.class));
    }

    /**
     * Collect the unboxed arguments and box them into the arguments Object[] array. Escape analysis
     * will later on virtualize the allocation and the boxings.
     */
    private static void intrinsifySpecializeArguments(GraphBuilderContext b, ValueNode callTargetNode, ValueNode originalArgumentsNode) {
        assert callTargetNode.isConstant();

        TypedEntryMethodBase method = (TypedEntryMethodBase) b.getGraph().method();
        if (method.specialized) {
            /* Register the assumptions that we collected in rootForCallTarget. */
            for (JavaConstant assumption : method.profiledTypesAssumptions) {
                b.getGraph().getAssumptions().record(new TruffleAssumption(assumption));
            }

            int parameterCount = method.getSignature().getParameterCount(false) - PREFIX_PARAMETER_COUNT;
            List<ValueNode> unboxedParameterNodes = new ArrayList<>();
            for (int i = 0; i < parameterCount; i++) {
                JavaType parameterType = method.getSignature().getParameterType(i + PREFIX_PARAMETER_COUNT, null);
                StampPair stamp = StampFactory.forDeclaredType(b.getGraph().getAssumptions(), parameterType, false);
                unboxedParameterNodes.add(b.append(new ParameterNode(i + PREFIX_PARAMETER_COUNT, stamp)));
            }
            ValueNode boxedParametersNode = insertBoxings(b, unboxedParameterNodes, method.getSignature());

            originalArgumentsNode.replaceAndDelete(boxedParametersNode);

            /*
             * We need to make sure that all deoptimizations of the method use a FrameState where
             * the arguments array is already virtualized, i.e., we need the StartNode to already
             * have a FrameState with the virtualized array. But that FrameState is only available
             * after escape analysis. The DeoptAnchorNode serves as a placeholder for the
             * FrameState, which we then later on transplant onto the StartNode.
             */
            DeoptAnchorNode deoptAnchor = b.append(new DeoptAnchorNode());
            BeginNode beginNode = b.append(new BeginNode());
            deoptAnchor.setSingleSuccessor(beginNode);
            method.deoptAnchor = deoptAnchor;

            FrameState state = b.getGraph().start().stateAfter();
            b.getGraph().start().setStateAfter(null);
            deoptAnchor.setStateAfter(state);

            b.push(JavaKind.Object, boxedParametersNode);

        } else {
            b.push(JavaKind.Object, originalArgumentsNode);
        }
    }

    static void afterTruffleTier(StructuredGraph graph) {
        TypedEntryMethodBase method = (TypedEntryMethodBase) graph.method();
        DeoptAnchorNode deoptAnchor = method.deoptAnchor;
        if (deoptAnchor != null) {
            if (graph.start().next() != deoptAnchor) {
                throw new PermanentBailoutException("Boxed parameters array must be virtualized now");
            }

            /*
             * Transplant the FrameState with the virtualized argument array from the placeholder
             * DeoptAnchordNode to the StartNode.
             */
            graph.start().setStateAfter(deoptAnchor.stateAfter());
            deoptAnchor.unlinkAndDelete();
            method.deoptAnchor = null;

            graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "After FrameState fixup");
        }
    }

    private static boolean isIsolatedCompilation() {
        return SubstrateOptions.shouldCompileInIsolates();
    }

    /**
     * Call from compiled code to either compiled code, or via an C2I adapter to the interpreter.
     * For type-specialized direct Truffle calls, this is a function pointer call with the unboxed
     * signature. For non-specialized calls (including all indirect Truffle calls), this is a
     * function pointer call with the arguments Object[] array.
     */
    private void intrinsifyInvokeFromCompiled(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode callTargetNode, ValueNode boxedParametersNode) {
        assert b.getMetaAccess() == SubstrateMetaAccess.singleton() : "Unexpected MetaAccessProvider";

        SubstrateSignature compiledSignature = null;
        List<ValueNode> unboxedParameters = new ArrayList<>();
        unboxedParameters.add(callTargetNode);

        if (callTargetNode.isConstant()) {
            if (isIsolatedCompilation()) {
                ClientHandle<?> callTargetHandle = ((IsolatedObjectConstant) callTargetNode.asJavaConstant()).getHandle();
                CompilerHandle<SubstrateSignature> signatureHandle = trySpecializeInvokeInClient(
                                IsolatedCompileContext.get().getClient(), callTargetHandle);
                compiledSignature = IsolatedCompileContext.get().unhand(signatureHandle);
            } else {
                SubstrateOptimizedCallTarget callTarget = (SubstrateOptimizedCallTarget) SubstrateObjectConstant.asObject(callTargetNode.asJavaConstant());
                compiledSignature = (SubstrateSignature) trySpecializeInvoke(callTarget);
            }
        }

        Computation[] addressComputation;
        if (compiledSignature != null) {
            insertUnboxings(b, boxedParametersNode, compiledSignature, unboxedParameters);
            addressComputation = new Computation[]{
                            new FieldLoad(fromCompiledSpecializedEntryPointField)
            };
        } else {
            compiledSignature = unspecializedSignature(SubstrateMetaAccess.singleton());
            unboxedParameters.add(boxedParametersNode);

            /*-
               Address computation that is performed:

               long entry = callTarget.fromCompiledUnspecializedEntryPoint;
               if (entry == 0) {
                 entry = TypedEntrySupport.singleton().compiledIndirectEntryAddress;
               }
             */
            RuntimeConfiguration runtimeConfiguration = RuntimeCompilationSupport.getRuntimeConfig();
            JavaConstant typedEntrySupport = runtimeConfiguration.getProviders().getSnippetReflection().forObject(this);
            addressComputation = new Computation[]{
                            new FieldLoad(fromCompiledUnspecializedEntryPointField),
                            new FieldLoadIfZero(typedEntrySupport, compiledIndirectEntryAddressField)
            };
        }

        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        JavaKind compiledSignatureReturnKind = compiledSignature.getReturnKind();
        if (returnKind != compiledSignatureReturnKind) {
            /*
             * There must be an already invalidated assumption registered for the compiled method,
             * so compilation would bail out before code installation. But we cannot easily check
             * that, especially when compiling in a separate isolate. So we are paranoid and exit
             * compilation immediately.
             */
            throw new RetryableBailoutException("Specialized return type of call target changed while compiling code from %s to %s", returnKind, compiledSignatureReturnKind);
        }

        StampPair returnStamp = StampPair.createSingle(StampFactory.forKind(returnKind));
        CallTargetNode indirectCallTargetNode = b.add(new ComputedIndirectCallTargetNode(callTargetNode, addressComputation, unboxedParameters.toArray(new ValueNode[unboxedParameters.size()]),
                        returnStamp, compiledSignature.toParameterTypes(null), null));

        b.handleReplacedInvoke(indirectCallTargetNode, returnKind);
    }

    private Object trySpecializeInvoke(SubstrateOptimizedCallTarget callTarget) {
        ArgumentsProfile argumentsProfile = callTarget.getArgumentsProfile();
        Class<?>[] argumentClasses = SubstrateOptimizedCallTarget.specializedArgumentClasses(argumentsProfile);
        ReturnProfile returnProfile = callTarget.getReturnProfile();
        Class<?> returnClass = getSpecializedReturnClass(argumentsProfile, returnProfile);

        SubstrateSignature specializedSignature = null;
        CompilerHandle<SubstrateSignature> specializedSignatureInCompiler = IsolatedHandles.nullHandle();
        if (argumentClasses != null) {
            specializedSignature = adaptSignature(argumentClasses, returnClass, true);
            if (isIsolatedCompilation()) {
                specializedSignatureInCompiler = createAdaptedSignatureInCompiler(argumentClasses, returnClass, true);
            }
        }
        SubstrateInstalledCode specializedAdapter = null;
        if (specializedSignature != null) {
            specializedAdapter = getOrCompileSpecializedC2IAdapter(callTarget, specializedSignature, specializedSignatureInCompiler);
        }
        callTarget.installSpecializedC2IAdapter(specializedAdapter, argumentsProfile.getAssumption(), returnClass != Object.class ? returnProfile.getAssumption() : null);
        return isIsolatedCompilation() ? specializedSignatureInCompiler.rawValue() : specializedSignature;
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<SubstrateSignature> trySpecializeInvokeInClient(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<?> callTargetHandle) {

        SubstrateOptimizedCallTarget callTarget = (SubstrateOptimizedCallTarget) IsolatedCompileClient.get().unhand(callTargetHandle);
        long specializedSignatureHandleValue = (long) singleton().trySpecializeInvoke(callTarget);
        return Word.signed(specializedSignatureHandleValue);
    }

    private SubstrateInstalledCode getOrCompileSpecializedC2IAdapter(OptimizedCallTarget callTarget, SubstrateSignature signature, CompilerHandle<SubstrateSignature> signatureInCompiler) {
        return adapters.computeIfAbsent(signatureToName("c2i", signature, true),
                        new Function<>() {
                            @Override
                            public SubstrateInstalledCode apply(String name) {
                                return compileC2IAdapter(callTarget, name, signature, signatureInCompiler);
                            }
                        });
    }

    private SubstrateInstalledCode getOrCompileI2CAdapter(OptimizedCallTarget callTarget, SubstrateSignature signature,
                    CompilerHandle<SubstrateSignature> signatureInCompiler, boolean specialized) {
        return adapters.computeIfAbsent(signatureToName("i2c", signature, specialized),
                        new Function<>() {
                            @Override
                            public SubstrateInstalledCode apply(String name) {
                                return compileI2CAdapter(callTarget, name, signature, signatureInCompiler, specialized);
                            }
                        });
    }

    void tryRegisterAdapter(TypedEntryAdapterInstalledCode installedCode) {
        assert installedCode.isValid();
        adapters.putIfAbsent(installedCode.getName(), installedCode);
    }

    private TypedEntryAdapterInstalledCode compileC2IAdapter(OptimizedCallTarget callTarget, String name, SubstrateSignature compiledSignature,
                    CompilerHandle<SubstrateSignature> signatureInCompiler) {
        if (isIsolatedCompilation()) {
            TypedAdapterMethod method = new TypedAdapterMethod(name, compiledSignature);
            IsolatedCompileClient context = IsolatedCompileClient.get();
            ClientHandle<SubstrateInstalledCode> codeHandle = compileC2IAdapterInIsolate(
                            context.getCompiler(), context.hand(method), context.createStringInCompiler(name), signatureInCompiler,
                            context.hand(() -> new TypedEntryAdapterInstalledCode(name)));
            return (TypedEntryAdapterInstalledCode) context.unhand(codeHandle);
        } else {
            TypedAdapterMethod method = new TypedAdapterMethod(name, compiledSignature);
            StructuredGraph graph = createC2IAdapterGraph(method, compiledSignature);
            return compileAndInstallAdapter(callTarget, graph);
        }
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileContext.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileContext.ExceptionRethrowCallerEpilogue.class)
    private static ClientHandle<SubstrateInstalledCode> compileC2IAdapterInIsolate(@SuppressWarnings("unused") CompilerIsolateThread compiler,
                    ClientHandle<TypedAdapterMethod> clientAdapterHandle, CompilerHandle<String> nameHandle, CompilerHandle<SubstrateSignature> signatureHandle,
                    ClientHandle<? extends SubstrateInstalledCode.Factory> installedCodeFactoryHandle) {

        IsolatedCompileContext context = IsolatedCompileContext.get();
        String name = context.unhand(nameHandle);
        SubstrateSignature signature = context.unhand(signatureHandle);
        TypedAdapterMethod method = new IsolatedTypedAdapterMethod(name, signature, clientAdapterHandle);
        StructuredGraph graph = singleton().createC2IAdapterGraph(method, context.unhand(signatureHandle));
        return compileAndInstallInClient(clientAdapterHandle, graph, installedCodeFactoryHandle);
    }

    private static ClientHandle<SubstrateInstalledCode> compileAndInstallInClient(ClientHandle<TypedAdapterMethod> clientMethodHandle,
                    StructuredGraph graph, ClientHandle<? extends SubstrateInstalledCode.Factory> installedCodeFactoryHandle) {
        TypedAdapterMethod method = (TypedAdapterMethod) graph.method();
        CompilationResult compilationResult = SubstrateGraalUtils.compileGraph(method, graph);
        return IsolatedRuntimeCodeInstaller.installInClientIsolate(method, clientMethodHandle, compilationResult, installedCodeFactoryHandle);
    }

    /**
     * Collect the unboxed arguments (according to the provided compiled code signature) and box
     * them into a newly allocated Object[] array. The call to the interpreter is then a regular
     * Java method call to the interpreter entry point.
     */
    private StructuredGraph createC2IAdapterGraph(TypedAdapterMethod method, SubstrateSignature compiledSignature) {
        SubstrateGraphKit kit = createAdapter(method);
        List<ValueNode> adapterParameterNodes = kit.getInitialArguments();
        ValueNode receiverNode = adapterParameterNodes.get(0);
        List<ValueNode> unboxedParameterNodes = adapterParameterNodes.subList(PREFIX_PARAMETER_COUNT, adapterParameterNodes.size());

        ValueNode boxedParametersNode = insertBoxings(kit, unboxedParameterNodes, compiledSignature);

        JavaKind returnKind = compiledSignature.getReturnKind();
        ValueNode resultNode = kit.createJavaCallWithExceptionAndUnwind(InvokeKind.Static, interpreterEntryMethods.get(returnKind), receiverNode, boxedParametersNode);
        kit.createReturn(resultNode, returnKind);

        return kit.finalizeGraph();
    }

    private static TypedEntryAdapterInstalledCode compileI2CAdapter(OptimizedCallTarget callTarget, String name, SubstrateSignature compiledSignature,
                    CompilerHandle<SubstrateSignature> signatureInCompiler, boolean specialized) {
        if (isIsolatedCompilation()) {
            TypedAdapterMethod method = new TypedAdapterMethod(name, compiledSignature);
            IsolatedCompileClient context = IsolatedCompileClient.get();
            ClientHandle<SubstrateInstalledCode> codeHandle = compileI2CAdapterInIsolateAndInstall(
                            context.getCompiler(), context.hand(method), context.createStringInCompiler(name), signatureInCompiler, specialized,
                            context.hand(() -> new TypedEntryAdapterInstalledCode(name)));
            return (TypedEntryAdapterInstalledCode) context.unhand(codeHandle);
        } else {
            TypedAdapterMethod method = new TypedAdapterMethod(name, createI2CAdapterSignature());
            StructuredGraph graph = createI2CAdapterGraph(method, compiledSignature, specialized);
            return compileAndInstallAdapter(callTarget, graph);
        }
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileContext.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileContext.ExceptionRethrowCallerEpilogue.class)
    private static ClientHandle<SubstrateInstalledCode> compileI2CAdapterInIsolateAndInstall(@SuppressWarnings("unused") CompilerIsolateThread compiler,
                    ClientHandle<TypedAdapterMethod> clientAdapterHandle, CompilerHandle<String> nameHandle, CompilerHandle<SubstrateSignature> signatureHandle,
                    boolean specialized, ClientHandle<? extends SubstrateInstalledCode.Factory> installedCodeFactoryHandle) {

        IsolatedCompileContext context = IsolatedCompileContext.get();
        SubstrateSignature interpreterSignature = createI2CAdapterSignature();
        String name = context.unhand(nameHandle);
        TypedAdapterMethod method = new IsolatedTypedAdapterMethod(name, interpreterSignature, clientAdapterHandle);
        StructuredGraph graph = createI2CAdapterGraph(method, context.unhand(signatureHandle), specialized);
        return compileAndInstallInClient(clientAdapterHandle, graph, installedCodeFactoryHandle);
    }

    private static SubstrateSignature createI2CAdapterSignature() {
        SubstrateMetaAccess metaAccess = SubstrateMetaAccess.singleton();
        SubstrateType[] parameterTypes = {metaAccess.lookupJavaType(SubstrateOptimizedCallTarget.class), metaAccess.lookupJavaType(long.class), metaAccess.lookupJavaType(Object[].class)};
        return new SubstrateSignature(parameterTypes, metaAccess.lookupJavaType(Object.class));
    }

    /**
     * Unbox the arguments Object[] array according to the provided compiled code signature. The
     * call to compiled code is then a function pointer call, and the function pointer is provided
     * as an argument to the adapter.
     *
     * Deoptimization can invalidate a function pointer to compiled code at any safeoint. Therefore,
     * the code path from the read of the function pointer in
     * {@link SubstrateOptimizedCallTarget#doInvoke} through the I2C adapter to the actual
     * compiled code must be uninterruptible, i.e., the I2C adapter must never block. For example,
     * the I2C adapter must not allocate before the invocation.
     *
     * The boxing of the return value requires allocation. But that allocation is OK because it is
     * after the invocation. The boxing must be done in the adapter because at the call site in the
     * interpreter, the specialized return type is not known yet. Only after looking up the adapter
     * (which must be atomic with regard to the invocation of the adapter), the specialized return
     * type is known.
     */
    private static StructuredGraph createI2CAdapterGraph(TypedAdapterMethod method, SubstrateSignature compiledSignature, boolean specialized) {
        SubstrateGraphKit kit = createAdapter(method);

        ValueNode truffleCallTargetNode = kit.loadLocal(0, JavaKind.Object);
        ValueNode entryPointNode = kit.loadLocal(1, JavaKind.Long); // two slots
        ValueNode boxedParametersNode = kit.loadLocal(3, JavaKind.Object);

        List<ValueNode> compiledParameterNodes = new ArrayList<>();
        compiledParameterNodes.add(truffleCallTargetNode);
        if (specialized) {
            insertUnboxings(kit, boxedParametersNode, compiledSignature, compiledParameterNodes);
        } else {
            compiledParameterNodes.add(boxedParametersNode);
        }

        ValueNode result = kit.createIndirectCall(entryPointNode, compiledParameterNodes, compiledSignature, SubstrateCallingConventionKind.Java);
        JavaKind returnKind = compiledSignature.getReturnKind();
        if (returnKind != JavaKind.Object) {
            result = kit.append(BoxNode.create(result, kit.getMetaAccess().lookupJavaType(returnKind.toBoxedJavaClass()), returnKind));
        }
        kit.createReturn(result, JavaKind.Object);

        return kit.finalizeGraph();
    }

    private static SubstrateGraphKit createAdapter(TypedAdapterMethod method) {
        RuntimeConfiguration runtimeConfig = RuntimeCompilationSupport.getRuntimeConfig();
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
        SubstrateCompilationIdentifier compilationId = new SubstrateCompilationIdentifier();
        RuntimeOptionValues options = RuntimeOptionValues.singleton();
        DebugContext debug = new Builder(options.get(), new GraalDebugHandlersFactory(runtimeConfig.getProviders().getSnippetReflection())).build();
        SubstrateGraphKit kit = new SubstrateGraphKit(debug, method, runtimeConfig.getProviders(), plugins, compilationId, false);
        kit.getGraph().getGraphState().configureExplicitExceptionsNoDeopt();
        return kit;
    }

    private static TypedEntryAdapterInstalledCode compileAndInstallAdapter(OptimizedCallTarget callTarget, StructuredGraph graph) {
        assert !isIsolatedCompilation();

        TypedAdapterMethod method = (TypedAdapterMethod) graph.method();
        CompilationResult compilationResult = SubstrateGraalUtils.compileGraph(method, graph);
        TypedEntryAdapterInstalledCode installedCode = new TypedEntryAdapterInstalledCode(compilationResult.getName());
        RuntimeCodeInstaller.install(method, compilationResult, installedCode);

        if (callTarget.getOptionValue(TraceCompilation)) {
            callTarget.log("created adapter " + method.getName() + " | CodeAddress 0x" + Long.toHexString(installedCode.getEntryPoint()));
        }

        return installedCode;
    }

    private static ValueNode insertBoxings(GraphBuilderTool b, List<ValueNode> unboxedParameters, SubstrateSignature compiledSignature) {
        int parameterCount = unboxedParameters.size();
        assert parameterCount == compiledSignature.getParameterCount(false) - PREFIX_PARAMETER_COUNT;
        NewArrayNode boxedParametersNode = b.append(new NewArrayNode(b.getMetaAccess().lookupJavaType(Object.class), ConstantNode.forInt(parameterCount, b.getGraph()), true));

        for (int i = 0; i < parameterCount; i++) {
            ValueNode parameterNode = unboxedParameters.get(i);
            JavaKind parameterKind = compiledSignature.getParameterKind(i + PREFIX_PARAMETER_COUNT);
            if (parameterKind.isPrimitive()) {
                parameterNode = b.append(BoxNode.create(parameterNode, b.getMetaAccess().lookupJavaType(parameterKind.toBoxedJavaClass()), parameterKind));
            }
            var storeIndexed = b.append(new StoreIndexedNode(boxedParametersNode, ConstantNode.forInt(i, b.getGraph()), null, null, JavaKind.Object, parameterNode));
            // This is artificial code without a correct framestate for deoptimization.
            var fs = b.append(new FrameState(BytecodeFrame.INVALID_FRAMESTATE_BCI));
            fs.invalidateForDeoptimization();
            storeIndexed.setStateAfter(fs);
        }
        return boxedParametersNode;
    }

    private static void insertUnboxings(GraphBuilderTool b, ValueNode boxedParametersNode, SubstrateSignature compiledSignature, List<ValueNode> unboxedParameters) {
        int parameterCount = compiledSignature.getParameterCount(false) - PREFIX_PARAMETER_COUNT;
        for (int i = 0; i < parameterCount; i++) {
            JavaKind parameterKind = compiledSignature.getParameterKind(i + PREFIX_PARAMETER_COUNT);
            ValueNode parameterNode = b.append(new LoadIndexedNode(b.getGraph().getAssumptions(), boxedParametersNode, ConstantNode.forInt(i, b.getGraph()), null, JavaKind.Object));
            if (parameterKind.isPrimitive()) {
                parameterNode = b.append(new UnboxNode(parameterNode, parameterKind, b.getMetaAccess()));
            }
            unboxedParameters.add(parameterNode);
        }
    }

    private static String signatureToName(String prefix, Signature compiledSignature, boolean specialized) {
        StringBuilder result = new StringBuilder(prefix).append('_');
        if (specialized) {
            result.append(compiledSignature.getReturnKind().getTypeChar()).append('_');
            int parameterCount = compiledSignature.getParameterCount(false) - PREFIX_PARAMETER_COUNT;
            for (int i = 0; i < parameterCount; i++) {
                result.append(compiledSignature.getParameterKind(i + PREFIX_PARAMETER_COUNT).getTypeChar());
            }
        } else {
            result.append("unspecialized");
        }
        return result.toString();
    }

    private static SubstrateSignature adaptSignature(Class<?>[] argumentClasses, Class<?> returnClass, boolean specialized) {
        if (specialized) {
            return specializedSignature(SubstrateMetaAccess.singleton(), argumentClasses, returnClass);
        }
        return unspecializedSignature(SubstrateMetaAccess.singleton());
    }

    private static SubstrateSignature specializedSignature(SubstrateMetaAccess metaAccess, Class<?>[] argumentClasses, SubstrateType returnType) {
        List<SubstrateType> argumentTypes = new ArrayList<>(argumentClasses.length);
        argumentTypes.add(metaAccess.lookupJavaType(SubstrateOptimizedCallTarget.class));
        for (Class<?> argumentClass : argumentClasses) {
            argumentTypes.add(toUnboxedType(metaAccess, argumentClass));
        }
        return new SubstrateSignature(argumentTypes.toArray(new SubstrateType[argumentTypes.size()]), returnType);
    }

    private static SubstrateSignature specializedSignature(SubstrateMetaAccess metaAccess, Class<?>[] argumentClasses, Class<?> returnClass) {
        Class<?> actualReturnClass = toUnboxedClassWithDefault(returnClass, Object.class);
        SubstrateType returnType = metaAccess.lookupJavaType(actualReturnClass);
        return specializedSignature(metaAccess, argumentClasses, returnType);
    }

    private static SubstrateSignature unspecializedSignature(SubstrateMetaAccess metaAccess) {
        return new SubstrateSignature(
                        new SubstrateType[]{metaAccess.lookupJavaType(SubstrateOptimizedCallTarget.class), metaAccess.lookupJavaType(Object[].class)},
                        metaAccess.lookupJavaType(Object.class));
    }

    private static Class<?> filterSpecializedReturnClass(Class<?> returnClass) {
        if (TYPED_RETURN_KINDS.contains(JavaKind.fromJavaClass(toUnboxedClassWithDefault(returnClass, Object.class)))) {
            return returnClass;
        } else {
            return Object.class;
        }
    }

    private static SubstrateType toUnboxedType(SubstrateMetaAccess metaAccess, Class<?> boxedClass) {
        return metaAccess.lookupJavaType(toUnboxedClassWithDefault(boxedClass, Object.class));
    }
}

final class TypedEntryAdapterInstalledCode extends SubstrateInstalledCodeImpl implements AuxiliaryImageCodeObserver {
    TypedEntryAdapterInstalledCode(String name) {
        super(name, null);
    }

    @Override
    public void afterAllAuxiliaryImageCodeInstalled() {
        if (isValid()) {
            // the code might have been invalidated, e.g., because of CPU feature mismatch
            TypedEntrySupport.singleton().tryRegisterAdapter(this);
        }
    }
}

@NodeInfo(allowedUsageTypes = InputType.Anchor, cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
class DeoptAnchorNode extends ControlSplitNode implements StateSplit, ControlFlowAnchored {
    public static final NodeClass<DeoptAnchorNode> TYPE = NodeClass.create(DeoptAnchorNode.class);

    @Successor AbstractBeginNode singleSuccessor;
    @OptionalInput(InputType.State) FrameState stateAfter;

    protected DeoptAnchorNode() {
        super(TYPE, StampFactory.forVoid());
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    void setSingleSuccessor(AbstractBeginNode node) {
        updatePredecessor(singleSuccessor, node);
        singleSuccessor = node;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public double probability(AbstractBeginNode successor) {
        return getProfileData().getDesignatedSuccessorProbability();
    }

    @Override
    public int getSuccessorCount() {
        return 1;
    }

    @Override
    public boolean setProbability(AbstractBeginNode successor, BranchProbabilityData profileData) {
        throw new PermanentBailoutException("There is only one successor, so probability cannot change");
    }

    @Override
    public BranchProbabilityData getProfileData() {
        return BranchProbabilityNode.ALWAYS_TAKEN_PROFILE;
    }

    @Override
    public AbstractBeginNode getPrimarySuccessor() {
        return singleSuccessor;
    }

    void unlinkAndDelete() {
        FixedNode next = singleSuccessor;
        updatePredecessor(singleSuccessor, null);
        singleSuccessor = null;
        replaceAtPredecessor(next);
        safeDelete();
    }
}

/**
 * Method for C2I and I2C adapter.
 */
class TypedAdapterMethod extends AbstractAnnotated implements SharedRuntimeMethod {

    private final String name;
    private final Signature signature;
    private final ResolvedJavaType declaringClass;

    TypedAdapterMethod(String name, Signature signature) {
        this.name = name;
        this.signature = signature;
        MetaAccessProvider metaAccess = RuntimeCompilationSupport.getRuntimeConfig().getProviders().getMetaAccess();
        this.declaringClass = metaAccess.lookupJavaType(SubstrateOptimizedCallTarget.class);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getModifiers() {
        return Modifier.STATIC;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public boolean isEntryPoint() {
        return false;
    }

    @Override
    public boolean hasCalleeSavedRegisters() {
        return false;
    }

    @Override
    public SharedMethod[] getImplementations() {
        throw VMError.shouldNotReachHere("There are no virtual calls to this method that might need resolution");
    }

    @Override
    public boolean isDeoptTarget() {
        return false;
    }

    @Override
    public boolean canDeoptimize() {
        return false;
    }

    @Override
    public int getVTableIndex() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public SharedMethod getIndirectCallTarget() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public StubType getDeoptStubType() {
        return StubType.NoDeoptStub;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ImageCodeInfo getImageCodeInfo() {
        throw VMError.shouldNotReachHere("Used for runtime compilation only, adapters are not in the native image");
    }

    @Override
    public boolean hasImageCodeOffset() {
        throw VMError.shouldNotReachHere("Used for runtime compilation only, adapters are not in the native image");
    }

    @Override
    public int getImageCodeOffset() {
        throw VMError.shouldNotReachHere("Used for runtime compilation only, adapters are not in the native image");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getImageCodeDeoptOffset() {
        return 0;
    }

    @Override
    public boolean forceIndirectCall() {
        return false;
    }

    @Override
    public byte[] getCode() {
        return null;
    }

    @Override
    public int getCodeSize() {
        return 0;
    }

    @Override
    public int getMaxLocals() {
        return signature.getParameterCount(true) * 2;
    }

    @Override
    public int getMaxStackSize() {
        return 2;
    }

    @Override
    public boolean isSynthetic() {
        return false;
    }

    @Override
    public boolean isVarArgs() {
        return false;
    }

    @Override
    public boolean isBridge() {
        return false;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public boolean isDeclared() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public boolean isClassInitializer() {
        return false;
    }

    @Override
    public boolean isConstructor() {
        return false;
    }

    @Override
    public boolean canBeStaticallyBound() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return new StackTraceElement("adapter", name, "", bci);
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public void reprofile() {
    }

    @Override
    public ConstantPool getConstantPool() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public java.lang.reflect.Type[] getGenericParameterTypes() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public boolean canBeInlined() {
        return false;
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return true;
    }

    @Override
    public boolean shouldBeInlined() {
        return false;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return null;
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return null;
    }

    @Override
    public Constant getEncoding() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw VMError.shouldNotReachHere("Adapters must be compiled without speculative optimizations");
    }

    @Override
    public AnnotationsInfo getRawDeclaredAnnotationInfo() {
        return null;
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        return null;
    }

    @Override
    public int getEncodedGraphStartOffset() {
        throw VMError.shouldNotReachHere("Graal graph is constructed manually");
    }

    @Override
    public SubstrateCallingConventionKind getCallingConventionKind() {
        return SubstrateCallingConventionKind.Java;
    }

    @Override
    public boolean isUninterruptible() {
        return false;
    }

    @Override
    public boolean needSafepointCheck() {
        return true;
    }

    @Override
    public boolean isForeignCallTarget() {
        return false;
    }

    @Override
    public boolean isSnippet() {
        return false;
    }

    @Override
    public String toString() {
        return "TypedEntryAdapterMethod<" + name + ">";
    }

    @Override
    public CFunctionPointer getAOTEntrypoint() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public ResolvedJavaMethod getInterpreterMethod() {
        return null;
    }

    @Override
    public boolean isLambdaFormCompiled() {
        return false;
    }
}

abstract class TypedEntryMethodBase extends AbstractAnnotated implements SharedRuntimeMethod, TruffleMethod, SpeculationContextObject {

    final String name;
    final SubstrateSignature signature;
    final JavaConstant[] profiledTypesAssumptions;
    final boolean specialized;
    final SharedRuntimeMethod original;

    DeoptAnchorNode deoptAnchor;

    TypedEntryMethodBase(String name, SubstrateSignature signature, JavaConstant[] profiledTypesAssumptions, boolean specialized, SharedRuntimeMethod original) {
        this.name = name;
        this.signature = signature;
        this.profiledTypesAssumptions = profiledTypesAssumptions;
        this.specialized = specialized;
        this.original = original;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getModifiers() {
        return java.lang.reflect.Modifier.STATIC;
    }

    @Override
    public SubstrateSignature getSignature() {
        return signature;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj == original;
    }

    @Override
    public int hashCode() {
        return original.hashCode();
    }

    @Override
    public String toString() {
        return "TypedEntryMethod<" + name + ">";
    }

    /* All methods below just forward to original. */

    @Override
    public boolean isEntryPoint() {
        return original.isEntryPoint();
    }

    @Override
    public SubstrateCallingConventionKind getCallingConventionKind() {
        return original.getCallingConventionKind();
    }

    @Override
    public boolean hasCalleeSavedRegisters() {
        return original.hasCalleeSavedRegisters();
    }

    @Override
    public SharedMethod[] getImplementations() {
        return original.getImplementations();
    }

    @Override
    public boolean isDeoptTarget() {
        return original.isDeoptTarget();
    }

    @Override
    public boolean isUninterruptible() {
        return original.isUninterruptible();
    }

    @Override
    public boolean needSafepointCheck() {
        return original.needSafepointCheck();
    }

    @Override
    public boolean isForeignCallTarget() {
        return original.isForeignCallTarget();
    }

    @Override
    public boolean isSnippet() {
        return original.isSnippet();
    }

    @Override
    public PartialEvaluationMethodInfo getTruffleMethodInfo() {
        return ((TruffleMethod) original).getTruffleMethodInfo();
    }

    @Override
    public boolean canDeoptimize() {
        return original.canDeoptimize();
    }

    @Override
    public int getVTableIndex() {
        return original.getVTableIndex();
    }

    @Override
    public SharedMethod getIndirectCallTarget() {
        return original.getIndirectCallTarget();
    }

    @Override
    public StubType getDeoptStubType() {
        return original.getDeoptStubType();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ImageCodeInfo getImageCodeInfo() {
        return ImageCodeInfoProvider.getImageCodeInfo(original);
    }

    @Override
    public boolean hasImageCodeOffset() {
        return original.hasImageCodeOffset();
    }

    @Override
    public int getImageCodeOffset() {
        return original.getImageCodeOffset();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getImageCodeDeoptOffset() {
        return original.getImageCodeDeoptOffset();
    }

    @Override
    public boolean forceIndirectCall() {
        return false;
    }

    @Override
    public byte[] getCode() {
        return original.getCode();
    }

    @Override
    public int getCodeSize() {
        return original.getCodeSize();
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return original.getDeclaringClass();
    }

    @Override
    public int getMaxLocals() {
        return original.getMaxLocals();
    }

    @Override
    public int getMaxStackSize() {
        return original.getMaxStackSize();
    }

    @Override
    public boolean isSynthetic() {
        return original.isSynthetic();
    }

    @Override
    public boolean isVarArgs() {
        return original.isVarArgs();
    }

    @Override
    public boolean isBridge() {
        return original.isBridge();
    }

    @Override
    public boolean isDefault() {
        return original.isDefault();
    }

    @Override
    public boolean isDeclared() {
        return original.isDeclared();
    }

    @Override
    public boolean isClassInitializer() {
        return original.isClassInitializer();
    }

    @Override
    public boolean isConstructor() {
        return original.isConstructor();
    }

    @Override
    public boolean canBeStaticallyBound() {
        return original.canBeStaticallyBound();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return original.getExceptionHandlers();
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return original.asStackTraceElement(bci);
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        return original.getProfilingInfo(includeNormal, includeOSR);
    }

    @Override
    public void reprofile() {
        original.reprofile();
    }

    @Override
    public ConstantPool getConstantPool() {
        return original.getConstantPool();
    }

    @Override
    public java.lang.reflect.Type[] getGenericParameterTypes() {
        return original.getGenericParameterTypes();
    }

    @Override
    public boolean canBeInlined() {
        return original.canBeInlined();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return original.hasNeverInlineDirective();
    }

    @Override
    public boolean shouldBeInlined() {
        return original.shouldBeInlined();
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return original.getLineNumberTable();
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return original.getLocalVariableTable();
    }

    @Override
    public Constant getEncoding() {
        return original.getEncoding();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        return original.isInVirtualMethodTable(resolved);
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        return original.getSpeculationLog();
    }

    @Override
    public AnnotationsInfo getRawDeclaredAnnotationInfo() {
        throw GraalError.shouldNotReachHere("The getRawDeclaredAnnotationInfo method is not supported");
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        throw GraalError.shouldNotReachHere("The getTypeAnnotationInfo method is not supported");
    }

    @Override
    public int getEncodedGraphStartOffset() {
        return original.getEncodedGraphStartOffset();
    }

    @Override
    public SharedRuntimeMethod getOriginal() {
        return original;
    }

    @Override
    public CFunctionPointer getAOTEntrypoint() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public ResolvedJavaMethod getInterpreterMethod() {
        return null;
    }

    @Override
    public boolean isLambdaFormCompiled() {
        return original.isLambdaFormCompiled();
    }

    @Override
    public void accept(Visitor visitor) {
        // Use the original method for the speculation context
        visitor.visitObject(original);
    }
}

/**
 * During isolated compilation, acts as stand-in for the actual {@link TypedAdapterMethod} object in
 * the client isolate, and holds a handle to that object for installing the finished compiled code.
 */
final class IsolatedTypedAdapterMethod extends TypedAdapterMethod implements IsolatedCompilationMethod<TypedAdapterMethod> {
    private final ClientHandle<TypedAdapterMethod> clientHandle;

    IsolatedTypedAdapterMethod(String name, Signature signature, ClientHandle<TypedAdapterMethod> clientHandle) {
        super(name, signature);
        this.clientHandle = clientHandle;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ClientHandle<TypedAdapterMethod> getMirror() {
        return clientHandle;
    }
}

/**
 * Wrapper for the partial evaluation entry point method that changes the signature to the
 * type-specialized signature.
 */
final class TypedEntryMethod extends TypedEntryMethodBase {
    final SubstrateInstalledCode i2cAdapter;

    TypedEntryMethod(String name, SubstrateSignature signature, JavaConstant[] profiledTypesAssumptions, boolean specialized, SharedRuntimeMethod original, SubstrateInstalledCode i2cAdapter) {
        super(name, signature, profiledTypesAssumptions, specialized, original);
        this.i2cAdapter = i2cAdapter;
    }

}

/**
 * During isolated compilation, acts as stand-in for the actual {@link TypedEntryMethod} object in
 * the client isolate, and holds a handle to that object for installing the finished compiled code.
 */
final class IsolatedTypedEntryMethod extends TypedEntryMethodBase implements IsolatedCompilationMethod<TypedEntryMethod> {
    private final ClientHandle<TypedEntryMethod> clientHandle;

    IsolatedTypedEntryMethod(ClientHandle<TypedEntryMethod> clientHandle, String name, SubstrateSignature signature,
                    JavaConstant[] profiledTypesAssumptions, boolean specialized, SharedRuntimeMethod original) {
        super(name, signature, profiledTypesAssumptions, specialized, original);
        this.clientHandle = clientHandle;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ClientHandle<TypedEntryMethod> getMirror() {
        return clientHandle;
    }
}
