/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
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
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
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
import org.graalvm.compiler.replacements.MethodHandlePlugin;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.graal.nodes.DeadEndNode;
import com.oracle.svm.core.graal.phases.TrustedInterfaceTypePlugin;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.core.jdk.VarHandleFeature;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.NativeImageUtil;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.c.GraalAccess;
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

    static class IntrinsificationRegistry extends IntrinsificationPluginRegistry {
    }

    private final boolean analysis;
    private final Providers parsingProviders;
    private final Providers universeProviders;
    private final AnalysisUniverse aUniverse;
    private final HostedUniverse hUniverse;

    private final ClassInitializationPlugin classInitializationPlugin;

    private final IntrinsificationRegistry intrinsificationRegistry;

    private final ResolvedJavaType methodHandleType;
    private final Set<String> methodHandleInvokeMethodNames;

    private final Class<?> varHandleClass;
    private final ResolvedJavaType varHandleType;
    private final Field varHandleVFormField;
    private final Method varFormInitMethod;

    private static final Method unsupportedFeatureMethod = ReflectionUtil.lookupMethod(VMError.class, "unsupportedFeature", String.class);

    public IntrinsifyMethodHandlesInvocationPlugin(boolean analysis, Providers providers, AnalysisUniverse aUniverse, HostedUniverse hUniverse) {
        this.analysis = analysis;
        this.aUniverse = aUniverse;
        this.hUniverse = hUniverse;
        this.universeProviders = providers;

        Providers originalProviders = GraalAccess.getOriginalProviders();
        this.parsingProviders = new Providers(originalProviders).copyWith(new MethodHandlesMetaAccessExtensionProvider());

        this.classInitializationPlugin = new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM());

        if (analysis) {
            intrinsificationRegistry = new IntrinsificationRegistry();
            ImageSingletons.add(IntrinsificationRegistry.class, intrinsificationRegistry);
        } else {
            intrinsificationRegistry = ImageSingletons.lookup(IntrinsificationRegistry.class);
        }

        methodHandleType = universeProviders.getMetaAccess().lookupJavaType(java.lang.invoke.MethodHandle.class);
        methodHandleInvokeMethodNames = new HashSet<>(Arrays.asList("invokeExact", "invoke", "invokeBasic", "linkToVirtual", "linkToStatic", "linkToSpecial", "linkToInterface"));

        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            try {
                varHandleClass = Class.forName("java.lang.invoke.VarHandle");
                varHandleType = universeProviders.getMetaAccess().lookupJavaType(varHandleClass);
                varHandleVFormField = ReflectionUtil.lookupField(varHandleClass, "vform");
                Class<?> varFormClass = Class.forName("java.lang.invoke.VarForm");
                varFormInitMethod = ReflectionUtil.lookupMethod(varFormClass, "getMethodType_V", int.class);
            } catch (ClassNotFoundException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        } else {
            varHandleClass = null;
            varHandleType = null;
            varHandleVFormField = null;
            varFormInitMethod = null;
        }
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        /*
         * We want to process invokes that have a constant MethodHandle parameter. And we need a
         * direct call, otherwise we do not have a single target method.
         */
        if (b.getInvokeKind().isDirect() && (hasMethodHandleArgument(args) || isVarHandleMethod(method, args))) {
            processInvokeWithMethodHandle(b, universeProviders.getReplacements(), method, args);
            return true;

        } else if (methodHandleType.equals(method.getDeclaringClass()) && methodHandleInvokeMethodNames.contains(method.getName())) {
            /*
             * The native methods defined in the class MethodHandle are currently not implemented at
             * all. Normally, we would mark them as @Delete to give the user a good error message.
             * Unfortunately, that does not work for the MethodHandle methods because they are
             * signature polymorphic, i.e., they exist in every possible signature. Therefore, we
             * must only look at the declaring class and the method name here.
             */
            reportUnsupportedFeature(b, method);
            return true;
        }

        return false;
    }

    private static boolean hasMethodHandleArgument(ValueNode[] args) {
        for (ValueNode argument : args) {
            if (argument.isConstant() && argument.getStackKind() == JavaKind.Object && SubstrateObjectConstant.asObject(argument.asJavaConstant()) instanceof MethodHandle) {
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
        if (method.getDeclaringClass().toJavaName(true).equals("java.lang.invoke.VarHandleGuards")) {
            if (args.length < 1 || !args[0].isJavaConstant() || !isVarHandle(args[0])) {
                throw new UnsupportedFeatureException("VarHandle object must be a compile time constant");
            }

            try {
                /*
                 * The field VarHandle.vform.methodType_V_table is a @Stable field but initialized
                 * lazily on first access. Therefore, constant folding can happen only after
                 * initialization has happened. We force initialization by invoking the method
                 * VarHandle.vform.getMethodType_V(0).
                 */
                Object varHandle = SubstrateObjectConstant.asObject(args[0].asJavaConstant());
                Object varForm = varHandleVFormField.get(varHandle);
                varFormInitMethod.invoke(varForm, 0);
            } catch (ReflectiveOperationException ex) {
                throw VMError.shouldNotReachHere(ex);
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean isVarHandle(ValueNode arg) {
        return varHandleType.isAssignableFrom(universeProviders.getMetaAccess().lookupJavaType(arg.asJavaConstant()));
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
            /* Avoid infinite recursion with a (more or less random) maximum depth. */
            if (b.getDepth() > 20) {
                return null;
            }

            String className = method.getDeclaringClass().toJavaName(true);
            if (className.startsWith("java.lang.invoke.VarHandle") && !className.equals("java.lang.invoke.VarHandle")) {
                /*
                 * Do not inline implementation methods of various VarHandle implementation classes.
                 * They are too complex and cannot be reduced to a single invoke or field access.
                 * There is also no need to inline them, because they are not related to any
                 * MethodHandle mechanism. Methods defined in VarHandle itself are fine and not
                 * covered by this rule.
                 */
                return null;
            } else if (className.startsWith("java.lang.invoke")) {
                /*
                 * Inline all helper methods used by method handles. We do not know exactly which
                 * ones they are, but they are all be from the same package.
                 */
                return createStandardInlineInfo(method);
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
    }

    private static void registerInvocationPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "java.lang.invoke.DirectMethodHandle", replacements);
        r.register1("ensureInitialized", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                /*
                 * Method handles for static methods have a guard that initializes the class (if the
                 * class was not yet initialized when the method handle was created). We emit the
                 * class initialization check manually later on when appending nodes to the target
                 * graph.
                 */
                return true;
            }
        });

        r = new Registration(plugins, "java.lang.invoke.Invokers", replacements);
        r.registerOptional1("maybeCustomize", MethodHandle.class, new InvocationPlugin() {
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
        r.register1("requireNonNull", Object.class, new InvocationPlugin() {
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
    }

    @SuppressWarnings("try")
    private void processInvokeWithMethodHandle(GraphBuilderContext b, Replacements replacements, ResolvedJavaMethod methodHandleMethod, ValueNode[] methodHandleArguments) {
        /*
         * When parsing for compilation, we must not intrinsify method handles that were not
         * intrinsified during analysis. Otherwise new code that was not seen as reachable by the
         * static analysis would be compiled.
         */
        if (!analysis && intrinsificationRegistry.get(b.getMethod(), b.bci()) != Boolean.TRUE) {
            reportUnsupportedFeature(b, methodHandleMethod);
            return;
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
        StructuredGraph graph = new StructuredGraph.Builder(b.getOptions(), debug).method(NativeImageUtil.toOriginal(methodHandleMethod)).build();
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

            Transplanter transplanter = new Transplanter(b, methodHandleMethod, transplanted);
            try {
                transplanter.graph(graph);

                if (analysis) {
                    /*
                     * Successfully intrinsified during analysis, remember that we can intrinsify
                     * when parsing for compilation.
                     */
                    intrinsificationRegistry.add(b.getMethod(), b.bci(), Boolean.TRUE);
                }
            } catch (AbortTransplantException ex) {
                /*
                 * The method handle cannot be intrinsified. The code that throws an error at
                 * runtime was already appended, so nothing more to do.
                 */
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
     */
    class Transplanter {
        private final BytecodeParser b;
        private final ResolvedJavaMethod methodHandleMethod;
        private final NodeMap<Node> transplanted;

        Transplanter(GraphBuilderContext b, ResolvedJavaMethod methodHandleMethod, NodeMap<Node> transplanted) {
            this.b = (BytecodeParser) b;
            this.methodHandleMethod = methodHandleMethod;
            this.transplanted = transplanted;
        }

        void graph(StructuredGraph graph) throws AbortTransplantException {
            JavaKind returnResultKind = b.getInvokeReturnType().getJavaKind().getStackKind();
            FixedNode oNode = graph.start().next();
            while (true) {
                if (fixedWithNextNode(oNode)) {
                    oNode = ((FixedWithNextNode) oNode).next();

                } else if (oNode instanceof ReturnNode) {
                    ReturnNode oReturn = (ReturnNode) oNode;
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

        private boolean fixedWithNextNode(FixedNode oNode) throws AbortTransplantException {
            if (oNode.getClass() == InvokeNode.class) {
                InvokeNode oInvoke = (InvokeNode) oNode;
                MethodCallTargetNode oCallTarget = (MethodCallTargetNode) oInvoke.callTarget();

                ResolvedJavaMethod tTargetMethod = lookup(oCallTarget.targetMethod());
                maybeEmitClassInitialization(b, oCallTarget.invokeKind() == InvokeKind.Static, tTargetMethod.getDeclaringClass());
                b.handleReplacedInvoke(oCallTarget.invokeKind(), tTargetMethod, nodes(oCallTarget.arguments()), false);

                JavaKind invokeResultKind = oInvoke.getStackKind();
                if (invokeResultKind != JavaKind.Void) {
                    /*
                     * The invoke was pushed by handleReplacedInvoke, pop it again. Note that the
                     * popped value is not necessarily an Invoke, because inlining during parsing
                     * and intrinsification can happen.
                     */
                    transplanted.put(oInvoke, b.pop(invokeResultKind));
                }
                return true;

            } else if (oNode.getClass() == FixedGuardNode.class) {
                FixedGuardNode oGuard = (FixedGuardNode) oNode;

                BytecodeExceptionKind tExceptionKind;
                ValueNode[] tExceptionArguments;
                if (oGuard.getReason() == DeoptimizationReason.NullCheckException) {
                    tExceptionKind = BytecodeExceptionKind.NULL_POINTER;
                    tExceptionArguments = new ValueNode[0];
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
                NewArrayNode tNew = b.add(new NewArrayNode(lookup(oNew.elementType()), node(oNew.length()), oNew.fillContents()));
                transplanted.put(oNew, tNew);
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

        private ValueNode node(ValueNode oNode) throws AbortTransplantException {
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
                List<Node> oNodes = Collections.singletonList(oNode);
                UnmodifiableEconomicMap<Node, Node> tNodes = b.getGraph().addDuplicates(oNodes, oNode.graph(), 1, transplanted);
                assert tNodes.size() == 1;
                tNode = tNodes.get(oNode);

            } else {
                throw bailout();
            }

            tNode = b.add((ValueNode) tNode);
            transplanted.put(oNode, tNode);
            return (ValueNode) tNode;
        }

        @SuppressWarnings("unchecked")
        private <T extends Stamp> T stamp(T oStamp) throws AbortTransplantException {
            Stamp result;
            if (((Stamp) oStamp).getClass() == ObjectStamp.class) {
                ObjectStamp oObjectStamp = (ObjectStamp) oStamp;
                result = new ObjectStamp(lookup(oObjectStamp.type()), oObjectStamp.isExactType(), oObjectStamp.nonNull(), oObjectStamp.alwaysNull());
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
            reportUnsupportedFeature(b, methodHandleMethod);
            /*
             * We need to get out of recursive transplant methods. Easier to use an exception than
             * to explicitly check every method invocation for a possible abort.
             */
            throw new AbortTransplantException();
        }
    }

    @SuppressWarnings("serial")
    static class AbortTransplantException extends Exception {
    }

    private static void reportUnsupportedFeature(GraphBuilderContext b, ResolvedJavaMethod methodHandleMethod) {
        String message = "Invoke with MethodHandle argument could not be reduced to at most a single call or single field access. " +
                        "The method handle must be a compile time constant, e.g., be loaded from a `static final` field. " +
                        "Method that contains the method handle invocation: " + methodHandleMethod.format("%H.%n(%p)");

        if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            /*
             * Ensure that we have space on the expression stack for the (unused) return value of
             * the invoke.
             */
            ((BytecodeParser) b).getFrameStateBuilder().clearStack();
            b.handleReplacedInvoke(InvokeKind.Static, b.getMetaAccess().lookupJavaMethod(unsupportedFeatureMethod),
                            new ValueNode[]{ConstantNode.forConstant(SubstrateObjectConstant.forObject(message), b.getMetaAccess(), b.getGraph())}, false);
            /* The invoked method throws an exception and therefore never returns. */
            b.append(new DeadEndNode());
            return;

        } else {
            throw new UnsupportedFeatureException(message + System.lineSeparator() + "To diagnose the issue, you can add the option " +
                            SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+") +
                            ". The error is then reported at run time when the invoke is executed.");
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
