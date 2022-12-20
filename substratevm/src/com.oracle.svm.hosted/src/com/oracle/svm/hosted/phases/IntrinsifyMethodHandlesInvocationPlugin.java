/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

import org.graalvm.collections.Pair;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ArithmeticOperation;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.OptionalInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.java.FinalFieldBarrierNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.compiler.replacements.MethodHandlePlugin;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.graal.phases.TrustedInterfaceTypePlugin;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.core.jdk.VarHandleFeature;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageUtil;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.snippets.IntrinsificationPluginRegistry;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Support for method handles that can be reduced to a plain invocation. This is enough to support
 * the method handles used for Java 8 Lambda expressions. Support for arbitrary method handles is
 * not possible in the Substrate VM, for the same reasons that we cannot support arbitrary
 * reflection.
 * <p>
 * Design decisions for this phase: To support static analysis, the method handle invocation needs
 * to be replaced with a regular invocation before static analysis runs. This requires inlining of
 * all method that are involved in the method handle dispatch. We introduce the restriction that one
 * method handle invocation can be reduced by exactly one regular invocation, so that we can inherit
 * the bci of that invocation, and there are no nested frame states created for the inlining. The
 * static analysis results are therefore registered using this bci, and the bci is still unique and
 * belongs to the original method.
 * <p>
 * Implementation: The starting point is an invoke that has a {@link Constant} {@link MethodHandle}
 * as an argument. The method handle is the appendix of the invokedynamic bytecode generated by
 * javac for lambda expressions. We inline recursively all methods that get the method handle
 * parameter, with special treatment of the invocation methods of the method handle chain provided
 * by {@link MethodHandleAccessProvider}. After all inlining (which is done during parsing,
 * configured by the {@link MethodHandlesInlineInvokePlugin}), we require to have just a single
 * invocation left. This invocation replaces the original invoke that was our starting point. If we
 * have more than a single invocation left, we fail and report it as an unsupported feature of
 * Substrate VM.
 * <p>
 * The parsing is done using the original universe and providers of the HotSpot VM. This has a
 * couple of advantages: Our analysis universe is not polluted with types and methods just used for
 * method handles; we can use the constant folding and graph builder plugins of the HotSpot VM; and
 * we can use the {@link MethodHandlePlugin} of the HotSpot VM without the need for any wrappers.
 * The downside is that we have to convert types, methods, and constants between our world and the
 * HotSpot world.
 */
public class IntrinsifyMethodHandlesInvocationPlugin implements NodePlugin {

    private static final Field varHandleVFormField;
    private static final Method varFormInitMethod;
    private static final Method varHandleGetMethodHandleMethod;

    static {
        varHandleVFormField = ReflectionUtil.lookupField(VarHandle.class, "vform");
        try {
            Class<?> varFormClass = Class.forName("java.lang.invoke.VarForm");
            varFormInitMethod = ReflectionUtil.lookupMethod(varFormClass, "getMethodType_V", int.class);
            varHandleGetMethodHandleMethod = ReflectionUtil.lookupMethod(VarHandle.class, "getMethodHandle", int.class);
        } catch (ClassNotFoundException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    public static class IntrinsificationRegistry extends IntrinsificationPluginRegistry {
    }

    private final ParsingReason reason;
    private final Providers parsingProviders;
    private final HostedProviders universeProviders;
    private final AnalysisUniverse aUniverse;
    private final HostedUniverse hUniverse;

    private final ClassInitializationPlugin classInitializationPlugin;

    private final IntrinsificationRegistry intrinsificationRegistry;

    private final ResolvedJavaType methodHandleType;
    private final ResolvedJavaType varHandleType;

    public IntrinsifyMethodHandlesInvocationPlugin(ParsingReason reason, HostedProviders providers, AnalysisUniverse aUniverse, HostedUniverse hUniverse) {
        this.reason = reason;
        this.aUniverse = aUniverse;
        this.hUniverse = hUniverse;
        this.universeProviders = providers;

        Providers originalProviders = GraalAccess.getOriginalProviders();
        this.parsingProviders = new Providers(originalProviders).copyWith(new MethodHandlesMetaAccessExtensionProvider());

        this.classInitializationPlugin = new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM());

        if (reason == ParsingReason.PointsToAnalysis) {
            intrinsificationRegistry = new IntrinsificationRegistry();
            ImageSingletons.add(IntrinsificationRegistry.class, intrinsificationRegistry);
        } else {
            intrinsificationRegistry = ImageSingletons.lookup(IntrinsificationRegistry.class);
        }

        methodHandleType = universeProviders.getMetaAccess().lookupJavaType(MethodHandle.class);
        varHandleType = universeProviders.getMetaAccess().lookupJavaType(VarHandle.class);
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] originalArgs) {
        ValueNode receiverForNullCheck = null;
        ValueNode[] args = originalArgs;
        if ((!method.isStatic() || isVarHandleGuards(method)) && args.length > 0 && args[0] instanceof PhiNode) {
            PhiNode phi = (PhiNode) args[0];
            /*
             * For loop phis, not all inputs are available yet since we have not parsed the loop
             * body yet.
             */
            if (!phi.isLoopPhi()) {
                ValueNode filteredReceiver = filterNullPhiInputs(phi);
                if (filteredReceiver != phi && filteredReceiver.isJavaConstant()) {
                    receiverForNullCheck = phi;
                    args = Arrays.copyOf(args, args.length);
                    args[0] = filteredReceiver;
                }
            }
        }

        /*
         * We want to process invokes that have a constant MethodHandle parameter. And we need a
         * direct call, otherwise we do not have a single target method.
         */
        if (b.getInvokeKind().isDirect() && (hasMethodHandleArgument(args) || isVarHandleMethod(method, args)) && !ignoreMethod(method)) {
            if (b.bciCanBeDuplicated()) {
                /*
                 * If we capture duplication of the bci, we don't process invoke.
                 */
                return false;
            } else {
                if (receiverForNullCheck != null) {
                    b.nullCheckedValue(receiverForNullCheck);
                }
                return processInvokeWithMethodHandle(b, universeProviders.getReplacements(), method, args);
            }

        } else if (methodHandleType.equals(method.getDeclaringClass())) {
            /*
             * The native methods defined in the class MethodHandle are currently not implemented at
             * all. Normally, we would mark them as @Delete to give the user a good error message.
             * Unfortunately, that does not work for the MethodHandle methods because they are
             * signature polymorphic, i.e., they exist in every possible signature. Therefore, we
             * must only look at the declaring class and the method name here.
             */
            return false;

        } else {
            return false;
        }
    }

    private static ValueNode filterNullPhiInputs(PhiNode phi) {
        ValueNode notAlwaysNullPhiInput = null;
        for (ValueNode phiInput : phi.values()) {
            if (StampTool.isPointerAlwaysNull(phiInput)) {
                /* Ignore always null phi inputs. */
            } else if (notAlwaysNullPhiInput != null) {
                /* More than one not-always-null phi inputs. Nothing more to optimize. */
                return phi;
            } else {
                /* First not-always-null phi input. */
                notAlwaysNullPhiInput = phiInput;
            }
        }
        return notAlwaysNullPhiInput;
    }

    private boolean hasMethodHandleArgument(ValueNode[] args) {
        for (ValueNode argument : args) {
            if (argument.isConstant() && argument.getStackKind() == JavaKind.Object &&
                            (((UniverseMetaAccess) universeProviders.getMetaAccess()).isInstanceOf(argument.asJavaConstant(), methodHandleType))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the method is the intrinsification root for a VarHandle. In the current VarHandle
     * implementation, all guards are in the automatically generated class VarHandleGuards. All
     * methods do have a VarHandle argument, and we expect it to be a compile-time constant.
     *
     * See the documentation in {@link VarHandleFeature} for more information on the overall
     * VarHandle support.
     */
    private boolean isVarHandleMethod(ResolvedJavaMethod method, ValueNode[] args) {
        /*
         * We do the check by class name because then we 1) do not need an explicit Java version
         * check (VarHandle was introduced with JDK 9), 2) VarHandleGuards is a non-public class
         * that we cannot reference by class literal, and 3) we do not need to worry about analysis
         * vs. hosted types. If the VarHandle implementation changes, we need to update our whole
         * handling anyway.
         */
        if (isVarHandleGuards(method)) {
            if (args.length < 1 || !args[0].isJavaConstant() || !isVarHandle(args[0])) {
                return false;
            }

            try {
                /*
                 * The field VarHandle.vform.methodType_V_table is a @Stable field but initialized
                 * lazily on first access. Therefore, constant folding can happen only after
                 * initialization has happened. We force initialization by invoking the method
                 * VarHandle.vform.getMethodType_V(0).
                 */
                VarHandle varHandle = (VarHandle) SubstrateObjectConstant.asObject(args[0].asJavaConstant());
                Object varForm = varHandleVFormField.get(varHandle);
                varFormInitMethod.invoke(varForm, 0);

                /*
                 * The AccessMode used for the access that we are going to intrinsify is hidden in a
                 * AccessDescriptor object that is also passed in as a parameter to the intrinsified
                 * method. Initializing all AccessMode enum values is easier than trying to extract
                 * the actual AccessMode.
                 */
                for (VarHandle.AccessMode accessMode : VarHandle.AccessMode.values()) {
                    /*
                     * Force initialization of the @Stable field VarHandle.vform.memberName_table.
                     * Starting with JDK 17, this field is lazily initialized.
                     */
                    boolean isAccessModeSupported = varHandle.isAccessModeSupported(accessMode);
                    /*
                     * Force initialization of the @Stable field
                     * VarHandle.typesAndInvokers.methodType_table.
                     */
                    varHandle.accessModeType(accessMode);

                    if (isAccessModeSupported) {
                        /*
                         * Force initialization of the @Stable field VarHandle.methodHandleTable (or
                         * VarHandle.typesAndInvokers.methodHandle_tabel on JDK <= 17) .
                         */
                        varHandleGetMethodHandleMethod.invoke(varHandle, accessMode.ordinal());
                    }
                }
            } catch (ReflectiveOperationException ex) {
                throw VMError.shouldNotReachHere(ex);
            }

            return true;
        } else {
            return false;
        }
    }

    private static boolean isVarHandleGuards(ResolvedJavaMethod method) {
        return method.getDeclaringClass().toJavaName(true).equals("java.lang.invoke.VarHandleGuards");
    }

    private boolean isVarHandle(ValueNode arg) {
        return varHandleType.isAssignableFrom(universeProviders.getMetaAccess().lookupJavaType(arg.asJavaConstant()));
    }

    private static final List<Pair<String, List<String>>> IGNORE_FILTER = Arrays.asList(
                    Pair.create("java.lang.invoke.MethodHandles", Arrays.asList("dropArguments", "filterReturnValue", "foldArguments", "insertArguments")),
                    Pair.create("java.lang.invoke.Invokers", Collections.singletonList("spreadInvoker")));

    private static boolean ignoreMethod(ResolvedJavaMethod method) {
        String className = method.getDeclaringClass().toJavaName(true);
        String methodName = method.getName();
        for (Pair<String, List<String>> ignored : IGNORE_FILTER) {
            if (ignored.getLeft().equals(className) && ignored.getRight().contains(methodName)) {
                return true;
            }
        }
        return false;
    }

    class MethodHandlesParameterPlugin implements ParameterPlugin {
        private final ValueNode[] methodHandleArguments;

        MethodHandlesParameterPlugin(ValueNode[] methodHandleArguments) {
            this.methodHandleArguments = methodHandleArguments;
        }

        @Override
        public FloatingNode interceptParameter(GraphBuilderTool b, int index, StampPair stamp) {
            if (methodHandleArguments[index].isConstant()) {
                /* Convert the constant from our world to the HotSpot world. */
                return ConstantNode.forConstant(toOriginal(methodHandleArguments[index].asJavaConstant()), parsingProviders.getMetaAccess());
            } else {
                /*
                 * Propagate the parameter type from our world to the HotSpot world. We have more
                 * precise type information from the arguments than the parameters of the method
                 * would be.
                 */
                Stamp argStamp = methodHandleArguments[index].stamp(NodeView.DEFAULT);
                ResolvedJavaType argType = StampTool.typeOrNull(argStamp);
                if (argType != null) {
                    // TODO For trustInterfaces = false, we cannot be more specific here
                    // (i.e. we cannot use TypeReference.createExactTrusted here)
                    TypeReference typeref = TypeReference.createWithoutAssumptions(NativeImageUtil.toOriginal(argType));
                    argStamp = StampTool.isPointerNonNull(argStamp) ? StampFactory.objectNonNull(typeref) : StampFactory.object(typeref);
                }
                return new ParameterNode(index, StampPair.createSingle(argStamp));
            }
        }
    }

    class MethodHandlesInlineInvokePlugin implements InlineInvokePlugin {
        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            /* Avoid infinite recursion and excessive graphs with (more or less random) limits. */
            if (b.getDepth() > 20 || b.getGraph().getNodeCount() > 1000) {
                return null;
            }

            String className = method.getDeclaringClass().toJavaName(true);
            if (className.startsWith("java.lang.invoke.VarHandle") && (!className.equals("java.lang.invoke.VarHandle") || method.getName().equals("getMethodHandleUncached"))) {
                /*
                 * Do not inline implementation methods of various VarHandle implementation classes.
                 * They are too complex and cannot be reduced to a single invoke or field access.
                 * There is also no need to inline them, because they are not related to any
                 * MethodHandle mechanism.
                 *
                 * Methods defined in VarHandle itself are fine and not covered by this rule, apart
                 * from well-known methods that are never useful to be inlined. If these methods are
                 * reached, intrinsification will not be possible in any case.
                 */
                return null;
            } else if (className.startsWith("java.lang.invoke") && !className.contains("InvokerBytecodeGenerator")) {
                /*
                 * Inline all helper methods used by method handles. We do not know exactly which
                 * ones they are, but they are all be from the same package.
                 */
                return createStandardInlineInfo(method);
            } else if (className.equals("sun.invoke.util.ValueConversions")) {
                /*
                 * Inline trivial helper methods for value conversion.
                 */
                return new InlineDuringParsingPlugin().shouldInlineInvoke(b, method, args);
            }
            return null;
        }
    }

    class MethodHandlesMetaAccessExtensionProvider implements MetaAccessExtensionProvider {
        @Override
        public JavaKind getStorageKind(JavaType type) {
            throw VMError.shouldNotReachHere("storage kind information is only needed for optimization phases not used by the method handle intrinsification");
        }

        @Override
        public boolean canConstantFoldDynamicAllocation(ResolvedJavaType type) {
            if (hUniverse == null) {
                /*
                 * During static analysis, every type can be constant folded and the static analysis
                 * will see the real allocation.
                 */
                return true;
            } else {
                ResolvedJavaType convertedType = optionalLookup(type);
                return convertedType != null && ((HostedType) convertedType).isInstantiated();
            }
        }

        @Override
        public boolean isGuaranteedSafepoint(ResolvedJavaMethod method, boolean isDirect) {
            throw VMError.shouldNotReachHere();
        }

        @Override
        public boolean canVirtualize(ResolvedJavaType instanceType) {
            return true;
        }
    }

    private static ResolvedJavaField findField(ResolvedJavaType type, String name) {
        for (ResolvedJavaField field : type.getInstanceFields(false)) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        throw GraalError.shouldNotReachHere("Required field " + name + " not found in " + type);
    }

    private void registerInvocationPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "java.lang.invoke.DirectMethodHandle", replacements);
        r.register(new RequiredInvocationPlugin("ensureInitialized", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                /*
                 * Method handles for static methods have a guard that initializes the class (if the
                 * class was not yet initialized when the method handle was created). We emit the
                 * class initialization check manually later on when appending nodes to the target
                 * graph.
                 */
                GraalError.guarantee(receiver.isConstant(), "Not a java constant %s", receiver.get());
                ResolvedJavaField memberField = findField(targetMethod.getDeclaringClass(), "member");
                JavaConstant member = b.getConstantReflection().readFieldValue(memberField, receiver.get().asJavaConstant());
                ResolvedJavaField clazzField = findField(memberField.getType().resolve(memberField.getDeclaringClass()), "clazz");
                JavaConstant clazz = b.getConstantReflection().readFieldValue(clazzField, member);
                b.add(new DirectMethodHandleEnsureInitializedNode(b.getConstantReflection().asJavaType(clazz)));
                return true;
            }
        });

        r = new Registration(plugins, "java.lang.invoke.Invokers", replacements);
        r.register(new OptionalInvocationPlugin("maybeCustomize", MethodHandle.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode mh) {
                /*
                 * JDK 8 update 60 added an additional customization possibility for method handles.
                 * For all use cases that we care about, that seems to be unnecessary, so we can
                 * just do nothing.
                 */
                return true;
            }
        });

        r = new Registration(plugins, Objects.class, replacements);
        r.register(new RequiredInvocationPlugin("requireNonNull", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode object) {
                /*
                 * Instead of inlining the method, intrinsify it to a pattern that we can easily
                 * detect when looking at the parsed graph.
                 */
                b.push(JavaKind.Object, b.addNonNullCast(object));
                return true;
            }
        });
        r = new Registration(plugins, MethodHandle.class, replacements);
        r.register(new RequiredInvocationPlugin("asType", Receiver.class, MethodType.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode newTypeNode) {
                ValueNode methodHandleNode = receiver.get(false);
                if (methodHandleNode.isJavaConstant() && newTypeNode.isJavaConstant()) {
                    /*
                     * If both, the MethodHandle and the MethodType are constant, we can evaluate
                     * asType eagerly and embed the result as a constant in the graph.
                     */
                    SnippetReflectionProvider snippetReflection = aUniverse.getOriginalSnippetReflection();
                    MethodHandle mh = snippetReflection.asObject(MethodHandle.class, methodHandleNode.asJavaConstant());
                    MethodType mt = snippetReflection.asObject(MethodType.class, newTypeNode.asJavaConstant());
                    if (mh == null || mt == null) {
                        return false;
                    }
                    final MethodHandle asType;
                    try {
                        asType = mh.asType(mt);
                    } catch (WrongMethodTypeException t) {
                        return false;
                    }
                    JavaConstant asTypeConstant = snippetReflection.forObject(asType);
                    ConstantNode asTypeNode = ConstantNode.forConstant(asTypeConstant, b.getMetaAccess(), b.getGraph());
                    b.push(JavaKind.Object, asTypeNode);
                    return true;
                }
                return false;
            }
        });
    }

    @SuppressWarnings("try")
    private boolean processInvokeWithMethodHandle(GraphBuilderContext b, Replacements replacements, ResolvedJavaMethod methodHandleMethod, ValueNode[] methodHandleArguments) {
        /*
         * When parsing for compilation, we must not intrinsify method handles that were not
         * intrinsified during analysis. Otherwise new code that was not seen as reachable by the
         * static analysis would be compiled.
         */
        if (reason != ParsingReason.PointsToAnalysis && intrinsificationRegistry.get(b.getMethod(), b.bci()) != Boolean.TRUE) {
            return false;
        }
        Plugins graphBuilderPlugins = new Plugins(parsingProviders.getReplacements().getGraphBuilderPlugins());

        registerInvocationPlugins(graphBuilderPlugins.getInvocationPlugins(), replacements);

        graphBuilderPlugins.prependParameterPlugin(new MethodHandlesParameterPlugin(methodHandleArguments));
        graphBuilderPlugins.clearInlineInvokePlugins();
        graphBuilderPlugins.prependInlineInvokePlugin(new MethodHandlesInlineInvokePlugin());
        graphBuilderPlugins.prependNodePlugin(new MethodHandlePlugin(parsingProviders.getConstantReflection().getMethodHandleAccess(), false));

        /* We do all the word type rewriting because parameters to the lambda can be word types. */
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        WordOperationPlugin wordOperationPlugin = new WordOperationPlugin(originalSnippetReflection, new SubstrateWordTypes(parsingProviders.getMetaAccess(), FrameAccess.getWordKind()));
        graphBuilderPlugins.appendInlineInvokePlugin(wordOperationPlugin);
        graphBuilderPlugins.appendTypePlugin(wordOperationPlugin);
        graphBuilderPlugins.appendTypePlugin(new TrustedInterfaceTypePlugin());
        graphBuilderPlugins.appendNodePlugin(wordOperationPlugin);
        graphBuilderPlugins.setClassInitializationPlugin(new NoClassInitializationPlugin());

        GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getSnippetDefault(graphBuilderPlugins);
        GraphBuilderPhase.Instance graphBuilder = new GraphBuilderPhase.Instance(parsingProviders, graphBuilderConfig, OptimisticOptimizations.NONE, null);

        DebugContext debug = b.getDebug();
        StructuredGraph graph = new StructuredGraph.Builder(b.getOptions(), debug)
                        .method(NativeImageUtil.toOriginal(methodHandleMethod))
                        .recordInlinedMethods(false)
                        .build();
        try (DebugContext.Scope s = debug.scope("IntrinsifyMethodHandles", graph)) {
            graphBuilder.apply(graph);
            /*
             * The canonicalizer converts unsafe field accesses for get/set method handles back to
             * high-level field load and store nodes.
             */
            CanonicalizerPhase.create().apply(graph, parsingProviders);

            debug.dump(DebugContext.VERBOSE_LEVEL, graph, "Intrinisfication graph before transplant");

            NodeMap<Node> transplanted = new NodeMap<>(graph);
            for (ParameterNode oParam : graph.getNodes(ParameterNode.TYPE)) {
                transplanted.put(oParam, methodHandleArguments[oParam.index()]);
            }

            Transplanter transplanter = new Transplanter(b, transplanted);
            try {
                transplanter.graph(graph);

                if (reason == ParsingReason.PointsToAnalysis) {
                    /*
                     * Successfully intrinsified during analysis, remember that we can intrinsify
                     * when parsing for compilation.
                     */
                    intrinsificationRegistry.add(b.getMethod(), b.bci(), Boolean.TRUE);
                }
                return true;
            } catch (AbortTransplantException ex) {
                /*
                 * The method handle cannot be intrinsified. If non-constant method handles are not
                 * supported, the code that throws an error at runtime was already appended, so
                 * nothing more to do. If non-constant method handles are supported, we return false
                 * so that the bytecode parser emit a regular invoke bytecode, i.e., the constant
                 * method handle is treated as if it were non-constant.
                 */
                return ex.handled;
            }
        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }

    /**
     * Transplants the graph parsed in the HotSpot universe into the currently parsed method. This
     * requires conversion of all types, methods, fields, and constants.
     *
     * Currently, only linear control flow in the original graph is supported. Nodes in the original
     * graph that have implicit exception edges ({@link InvokeNode}, {@link FixedGuardNode} are
     * converted to nodes with explicit exception edges. So the transplanted graph has a limited
     * amount of control flow for exception handling, but still no control flow merges.
     *
     * All necessary frame states use the same bci from the caller, i.e., all transplanted method
     * calls, field stores, exceptions, ... look as if they are coming from the original invocation
     * site of the method handle. This means the static analysis is not storing any analysis results
     * for these calls, because lookup of analysis results requires a unique bci.
     *
     * During this process, values are not pushed and popped from the frame state as usual. Instead,
     * at most one value is temporarily pushed onto the frame state's stack. During the generation
     * process {@link #tempFrameStackValue} is used to represent the value currently temporarily
     * pushed onto the stack.
     */
    class Transplanter {
        private final BytecodeParser b;
        private final NodeMap<Node> transplanted;
        private JavaKind tempFrameStackValue;

        Transplanter(GraphBuilderContext b, NodeMap<Node> transplanted) {
            this.b = (BytecodeParser) b;
            this.transplanted = transplanted;
            this.tempFrameStackValue = null;
        }

        void graph(StructuredGraph graph) throws AbortTransplantException {
            JavaKind returnResultKind = b.getInvokeReturnType().getJavaKind().getStackKind();
            FixedNode oNode = graph.start().next();
            while (true) {
                if (fixedWithNextNode(oNode)) {
                    oNode = ((FixedWithNextNode) oNode).next();

                } else if (oNode instanceof ReturnNode) {
                    ReturnNode oReturn = (ReturnNode) oNode;
                    /* Push the returned result. */
                    if (returnResultKind != JavaKind.Void) {
                        b.push(returnResultKind, node(oReturn.result()));
                    }
                    /* We are done. */
                    return;

                } else {
                    throw bailout();
                }
            }
        }

        /**
         * @return whether the current frame has enough space for a new value of the given kind to
         *         be pushed to the stack.
         */
        private boolean frameStackHasSpaceForKind(JavaKind javaKind) {
            return b.getFrameStateBuilder().stackSize() + (javaKind.needsTwoSlots() ? 2 : 1) <= b.getMethod().getMaxStackSize();
        }

        /**
         * If space is available, temporarily push {@code value} onto frame's stack.
         */
        private void pushToFrameStack(ValueNode value) {
            JavaKind kind = value.getStackKind();
            /* Pushing new value if there is space. */
            if (frameStackHasSpaceForKind(kind)) {
                b.push(kind, value);
                tempFrameStackValue = kind;
            }
        }

        /*
         * Remove temp value, if present, from stack.
         */
        private void popTempFrameStackValue() {
            if (tempFrameStackValue != null) {
                b.pop(tempFrameStackValue);
                tempFrameStackValue = null;
            }
        }

        private boolean fixedWithNextNode(FixedNode oNode) throws AbortTransplantException {
            if (oNode.getClass() == InvokeNode.class) {
                InvokeNode oInvoke = (InvokeNode) oNode;
                MethodCallTargetNode oCallTarget = (MethodCallTargetNode) oInvoke.callTarget();
                transplantInvoke(oInvoke, lookup(oCallTarget.targetMethod()), oCallTarget.invokeKind(), nodes(oCallTarget.arguments()), oCallTarget.returnKind());
                return true;

            } else if (oNode.getClass() == FixedGuardNode.class) {
                FixedGuardNode oGuard = (FixedGuardNode) oNode;

                BytecodeExceptionKind tExceptionKind;
                ValueNode[] tExceptionArguments;
                if (oGuard.getReason() == DeoptimizationReason.NullCheckException) {
                    tExceptionKind = BytecodeExceptionKind.NULL_POINTER;
                    tExceptionArguments = ValueNode.EMPTY_ARRAY;
                } else if (oGuard.getReason() == DeoptimizationReason.ClassCastException && oGuard.condition().getClass() == InstanceOfNode.class) {
                    /*
                     * Throwing the ClassCastException requires the checked object and the expected
                     * type as arguments, which we can get for the InstanceOfNode.
                     */
                    InstanceOfNode oCondition = (InstanceOfNode) oGuard.condition();
                    tExceptionKind = BytecodeExceptionKind.CLASS_CAST;
                    tExceptionArguments = new ValueNode[]{
                                    node(oCondition.getValue()),
                                    ConstantNode.forConstant(b.getConstantReflection().asJavaClass(lookup(oCondition.type().getType())), b.getMetaAccess(), b.getGraph())};
                } else {
                    /*
                     * Several other deoptimization reasons could be supported easily, but for now
                     * there is no need for them.
                     */
                    return false;
                }

                AbstractBeginNode tPassingSuccessor = b.emitBytecodeExceptionCheck((LogicNode) node(oGuard.condition()), !oGuard.isNegated(), tExceptionKind, tExceptionArguments);
                /*
                 * Anchor-usages of the guard are redirected to the BeginNode after the explicit
                 * exception check. If the check was eliminated, we add a new temporary BeginNode.
                 */
                transplanted.put(oGuard, tPassingSuccessor != null ? tPassingSuccessor : b.add(new BeginNode()));
                return true;

            } else if (oNode.getClass() == LoadFieldNode.class) {
                LoadFieldNode oLoad = (LoadFieldNode) oNode;
                ResolvedJavaField tTarget = lookup(oLoad.field());
                maybeEmitClassInitialization(b, tTarget.isStatic(), tTarget.getDeclaringClass());
                ValueNode tLoad = b.add(LoadFieldNode.create(null, node(oLoad.object()), tTarget));
                transplanted.put(oLoad, tLoad);
                return true;

            } else if (oNode.getClass() == StoreFieldNode.class) {
                StoreFieldNode oStore = (StoreFieldNode) oNode;
                ResolvedJavaField tTarget = lookup(oStore.field());
                maybeEmitClassInitialization(b, tTarget.isStatic(), tTarget.getDeclaringClass());
                b.add(new StoreFieldNode(node(oStore.object()), tTarget, node(oStore.value())));
                return true;

            } else if (oNode.getClass() == NewInstanceNode.class) {
                NewInstanceNode oNew = (NewInstanceNode) oNode;
                ResolvedJavaType tInstanceClass = lookup(oNew.instanceClass());
                maybeEmitClassInitialization(b, true, tInstanceClass);
                NewInstanceNode tNew = b.add(new NewInstanceNode(tInstanceClass, oNew.fillContents()));
                transplanted.put(oNew, tNew);
                return true;

            } else if (oNode.getClass() == NewArrayNode.class) {
                NewArrayNode oNew = (NewArrayNode) oNode;
                NewArrayNode tNew = b.add(new NewArrayNode(lookup(oNew.elementType()), b.maybeEmitExplicitNegativeArraySizeCheck(node(oNew.length())), oNew.fillContents()));
                transplanted.put(oNew, tNew);
                return true;

            } else if (oNode.getClass() == FinalFieldBarrierNode.class) {
                FinalFieldBarrierNode oNew = (FinalFieldBarrierNode) oNode;
                FinalFieldBarrierNode tNew = b.add(new FinalFieldBarrierNode(node(oNew.getValue())));
                transplanted.put(oNew, tNew);
                return true;

            } else if (oNode.getClass() == DirectMethodHandleEnsureInitializedNode.class) {
                DirectMethodHandleEnsureInitializedNode oInit = (DirectMethodHandleEnsureInitializedNode) oNode;
                ResolvedJavaType tInstanceClass = lookup(oInit.instanceClass());
                maybeEmitClassInitialization(b, true, tInstanceClass);
                return true;
            } else {
                return false;
            }
        }

        private ValueNode[] nodes(List<ValueNode> oNodes) throws AbortTransplantException {
            ValueNode[] tNodes = new ValueNode[oNodes.size()];
            for (int i = 0; i < tNodes.length; i++) {
                tNodes[i] = node(oNodes.get(i));
            }
            return tNodes;
        }

        private ValueNode node(Node oNode) throws AbortTransplantException {
            if (oNode == null) {
                return null;
            }
            Node tNode = transplanted.get(oNode);
            if (tNode != null) {
                return (ValueNode) tNode;
            }

            if (oNode.getClass() == ConstantNode.class) {
                ConstantNode oConstant = (ConstantNode) oNode;
                tNode = ConstantNode.forConstant(constant(oConstant.getValue()), universeProviders.getMetaAccess());

            } else if (oNode.getClass() == DirectMethodHandleEnsureInitializedNode.class) {
                return null;
            } else if (oNode.getClass() == PiNode.class) {
                PiNode oPi = (PiNode) oNode;
                tNode = new PiNode(node(oPi.object()), stamp(oPi.piStamp()), node(oPi.getGuard().asNode()));

            } else if (oNode.getClass() == InstanceOfNode.class) {
                InstanceOfNode oInstanceOf = (InstanceOfNode) oNode;
                tNode = InstanceOfNode.createHelper(stamp(oInstanceOf.getCheckedStamp()),
                                node(oInstanceOf.getValue()),
                                oInstanceOf.profile(),
                                (AnchoringNode) node((ValueNode) oInstanceOf.getAnchor()));

            } else if (oNode.getClass() == IsNullNode.class) {
                IsNullNode oIsNull = (IsNullNode) oNode;
                tNode = IsNullNode.create(node(oIsNull.getValue()));

            } else if (oNode instanceof ArithmeticOperation) {
                /*
                 * We consider all arithmetic operations as safe for transplant, since they do not
                 * have side effects and also do not reference any types or other elements that we
                 * would need to modify.
                 */
                for (Node input : oNode.inputs()) {
                    /*
                     * Make sure all input nodes are transplanted first, and registered in the
                     * transplanted map.
                     */
                    node(input);
                }
                List<Node> oNodes = Collections.singletonList(oNode);
                UnmodifiableEconomicMap<Node, Node> tNodes = b.getGraph().addDuplicates(oNodes, oNode.graph(), 1, transplanted);
                /*
                 * The following assertion looks strange, but NodeMap.size() is not implemented so
                 * we need to iterate the map to get the size.
                 */
                assert StreamSupport.stream(tNodes.getKeys().spliterator(), false).count() == 1;
                tNode = tNodes.get(oNode);

            } else {
                throw bailout();
            }

            tNode = b.add((ValueNode) tNode);
            assert tNode.verify();
            transplanted.put(oNode, tNode);
            return (ValueNode) tNode;
        }

        private void transplantInvoke(FixedWithNextNode oNode, ResolvedJavaMethod tTargetMethod, InvokeKind invokeKind, ValueNode[] arguments, JavaKind invokeResultKind) {
            maybeEmitClassInitialization(b, invokeKind == InvokeKind.Static, tTargetMethod.getDeclaringClass());

            if (invokeResultKind == JavaKind.Void) {
                /*
                 * Invokedynamics can be parsed into a NewInstanceNode & InvokeNode combo. In this
                 * situation, it is necessary to push the NewInstanceNode onto the stack so that it
                 * is included in the stateDuring FrameState of the InvokeNode.
                 */
                Node pred = oNode;
                do {
                    pred = pred.predecessor();
                    /*
                     * The prior NewInstanceNode may be guarded by a fixed guard (e.g., for an
                     * instanceof check). In this case, look at fixed guard's predecessor.
                     */
                } while (pred.getClass() == FixedGuardNode.class);
                if (pred.getClass() == NewInstanceNode.class && transplanted.containsKey(pred)) {
                    Node tNew = transplanted.get(pred);
                    pushToFrameStack((ValueNode) tNew);
                }
            }

            b.handleReplacedInvoke(invokeKind, tTargetMethod, arguments, false);

            if (invokeResultKind != JavaKind.Void) {
                /*
                 * The invoke was pushed by handleReplacedInvoke, pop it again. Note that the popped
                 * value is not necessarily an Invoke, because inlining during parsing and
                 * intrinsification can happen.
                 */
                transplanted.put(oNode, b.pop(invokeResultKind));
            } else {
                popTempFrameStackValue();
            }
        }

        @SuppressWarnings("unchecked")
        private <T extends Stamp> T stamp(T oStamp) throws AbortTransplantException {
            Stamp result;
            if (oStamp.getClass() == ObjectStamp.class) {
                ObjectStamp oObjectStamp = (ObjectStamp) oStamp;
                result = new ObjectStamp(lookup(oObjectStamp.type()), oObjectStamp.isExactType(), oObjectStamp.nonNull(), oObjectStamp.alwaysNull(), oObjectStamp.isAlwaysArray());
            } else if (oStamp instanceof PrimitiveStamp) {
                result = oStamp;
            } else {
                throw bailout();
            }
            assert oStamp.getClass() == result.getClass();
            return (T) result;
        }

        private JavaConstant constant(Constant oConstant) throws AbortTransplantException {
            JavaConstant tConstant;
            if (oConstant == JavaConstant.NULL_POINTER) {
                return JavaConstant.NULL_POINTER;
            } else if (oConstant instanceof JavaConstant) {
                tConstant = lookup((JavaConstant) oConstant);
            } else {
                throw bailout();
            }

            if (tConstant.getJavaKind() == JavaKind.Object) {
                /*
                 * The object replacer are not invoked when parsing in the HotSpot universe, so we
                 * also need to do call the replacer here.
                 */
                Object oldObject = aUniverse.getSnippetReflection().asObject(Object.class, tConstant);
                Object newObject = aUniverse.replaceObject(oldObject);
                if (newObject != oldObject) {
                    return aUniverse.getSnippetReflection().forObject(newObject);
                }
            }
            return tConstant;
        }

        private RuntimeException bailout() throws AbortTransplantException {
            boolean handled = false;
            /*
             * We need to get out of recursive transplant methods. Easier to use an exception than
             * to explicitly check every method invocation for a possible abort.
             */
            throw new AbortTransplantException(handled);
        }
    }

    @SuppressWarnings("serial")
    static class AbortTransplantException extends Exception {
        private final boolean handled;

        AbortTransplantException(boolean handled) {
            this.handled = handled;
        }
    }

    private void maybeEmitClassInitialization(GraphBuilderContext b, boolean isStatic, ResolvedJavaType declaringClass) {
        if (isStatic) {
            /*
             * We know that this code only runs during bytecode parsing, so the casts to
             * BytecodeParser are safe. We want to avoid putting additional rarely used methods into
             * GraphBuilderContext.
             */
            classInitializationPlugin.apply(b, declaringClass, () -> ((BytecodeParser) b).getFrameStateBuilder().create(b.bci(), (BytecodeParser) b.getNonIntrinsicAncestor(), false, null, null));
        }
    }

    private ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        ResolvedJavaMethod result = aUniverse.lookup(method);
        if (hUniverse != null) {
            result = hUniverse.lookup(result);
        }
        return result;
    }

    private ResolvedJavaField lookup(ResolvedJavaField field) {
        ResolvedJavaField result = aUniverse.lookup(field);
        if (hUniverse != null) {
            result = hUniverse.lookup(result);
        }
        return result;
    }

    private ResolvedJavaType lookup(ResolvedJavaType type) {
        ResolvedJavaType result = aUniverse.lookup(type);
        if (hUniverse != null) {
            result = hUniverse.lookup(result);
        }
        return result;
    }

    private ResolvedJavaType optionalLookup(ResolvedJavaType type) {
        ResolvedJavaType result = aUniverse.optionalLookup(type);
        if (result != null && hUniverse != null) {
            result = hUniverse.optionalLookup(result);
        }
        return result;
    }

    private JavaConstant lookup(JavaConstant constant) {
        return aUniverse.lookup(constant);
    }

    private JavaConstant toOriginal(JavaConstant constant) {
        return aUniverse.toHosted(constant);
    }
}

@NodeInfo(size = NodeSize.SIZE_16, cycles = NodeCycles.CYCLES_2, cyclesRationale = "Class initialization only runs at most once at run time, so the amortized cost is only the is-initialized check")
final class DirectMethodHandleEnsureInitializedNode extends FixedWithNextNode {

    public static final NodeClass<DirectMethodHandleEnsureInitializedNode> TYPE = NodeClass.create(DirectMethodHandleEnsureInitializedNode.class);

    private final ResolvedJavaType clazz;

    protected DirectMethodHandleEnsureInitializedNode(ResolvedJavaType clazz) {
        super(TYPE, StampFactory.forVoid());
        this.clazz = clazz;
    }

    public ResolvedJavaType instanceClass() {
        return clazz;
    }
}
