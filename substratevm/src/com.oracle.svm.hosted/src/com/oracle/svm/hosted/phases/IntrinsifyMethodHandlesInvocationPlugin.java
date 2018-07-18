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

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnaryOpLogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
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
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.MethodHandlePlugin;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.graal.phases.TrustedInterfaceTypePlugin;
import com.oracle.svm.core.jdk.VarHandleFeature;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
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
 * Design decisions for this phase: So support static analysis, the method handle invocation needs
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
    private final Providers originalProviders;
    private final Providers universeProviders;
    private final AnalysisUniverse aUniverse;
    private final HostedUniverse hUniverse;

    public IntrinsifyMethodHandlesInvocationPlugin(Providers providers, AnalysisUniverse aUniverse, HostedUniverse hUniverse) {
        this.aUniverse = aUniverse;
        this.hUniverse = hUniverse;
        this.universeProviders = providers;
        this.originalProviders = GraalAccess.getOriginalProviders();
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        /*
         * We want to process invokes that have a constant MethodHandle parameter. And we need a
         * direct call, otherwise we do not have a single target method.
         */
        if (b.getInvokeKind().isDirect() && (hasMethodHandleArgument(args) || isVarHandleMethod(method))) {
            processInvokeWithMethodHandle(b, universeProviders.getReplacements().getDefaultReplacementBytecodeProvider(), method, args);
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
     * methods do have a VarHandle argument, and we expect it to be a compile-time constant. But we
     * do not need to check that explicitly: if it is not a compile-time constant, then we are not
     * able to intrinsify the guard to a single call, and the intrinsification will fail later and
     * report an error to the user.
     *
     * See the documentation in {@link VarHandleFeature} for more information on the overall
     * VarHandle support.
     */
    private static boolean isVarHandleMethod(ResolvedJavaMethod method) {
        /*
         * We do the check by class name because then we 1) do not need an explicit Java version
         * check (VarHandle was introduced with JDK 9), 2) VarHandleGuards is a non-public class
         * that we cannot reference by class literal, and 3) we do not need to worry about analysis
         * vs. hosted types. If the VarHandle implementation changes, we need to update our whole
         * handling anyway.
         */
        return method.getDeclaringClass().toJavaName(true).equals("java.lang.invoke.VarHandleGuards");
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
                return ConstantNode.forConstant(toOriginal(methodHandleArguments[index].asJavaConstant()), originalProviders.getMetaAccess());
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
                    TypeReference typeref = TypeReference.createWithoutAssumptions(toOriginal(argType));
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
            if (className.startsWith("java.lang.invoke.VarHandle")) {
                /*
                 * Do not inline implementation methods of various VarHandle implementation classes.
                 * They are too complex and cannot be reduced to a single invoke or field access.
                 * There is also no need to inline them, because they are not related to any
                 * MethodHandle mechanism.
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

    private static void registerInvocationPlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, "java.lang.invoke.DirectMethodHandle", bytecodeProvider);
        r.register1("ensureInitialized", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                /*
                 * Method handles for static methods have a guard that initializes the class (if the
                 * class was not yet initialized when the method handle was created). We initialize
                 * the class during static analysis anyway, so we can just do nothing.
                 */
                return true;
            }
        });

        r = new Registration(plugins, "java.lang.invoke.Invokers", bytecodeProvider);
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
    }

    @SuppressWarnings("try")
    private void processInvokeWithMethodHandle(GraphBuilderContext b, BytecodeProvider bytecodeProvider, ResolvedJavaMethod methodHandleMethod, ValueNode[] methodHandleArguments) {
        Plugins graphBuilderPlugins = new Plugins(((ReplacementsImpl) originalProviders.getReplacements()).getGraphBuilderPlugins());

        registerInvocationPlugins(graphBuilderPlugins.getInvocationPlugins(), bytecodeProvider);

        graphBuilderPlugins.prependParameterPlugin(new MethodHandlesParameterPlugin(methodHandleArguments));
        graphBuilderPlugins.clearInlineInvokePlugins();
        graphBuilderPlugins.prependInlineInvokePlugin(new MethodHandlesInlineInvokePlugin());
        graphBuilderPlugins.prependNodePlugin(new MethodHandlePlugin(originalProviders.getConstantReflection().getMethodHandleAccess(), false));

        /* We do all the word type rewriting because parameters to the lambda can be word types. */
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        WordOperationPlugin wordOperationPlugin = new WordOperationPlugin(originalSnippetReflection, new WordTypes(originalProviders.getMetaAccess(), FrameAccess.getWordKind()));
        graphBuilderPlugins.appendInlineInvokePlugin(wordOperationPlugin);
        graphBuilderPlugins.appendTypePlugin(wordOperationPlugin);
        graphBuilderPlugins.appendTypePlugin(new TrustedInterfaceTypePlugin());
        graphBuilderPlugins.appendNodePlugin(wordOperationPlugin);

        GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getSnippetDefault(graphBuilderPlugins);
        GraphBuilderPhase.Instance graphBuilder = new GraphBuilderPhase.Instance(originalProviders.getMetaAccess(), originalProviders.getStampProvider(), originalProviders.getConstantReflection(),
                        originalProviders.getConstantFieldProvider(), graphBuilderConfig, OptimisticOptimizations.NONE, null);

        DebugContext debug = b.getDebug();
        StructuredGraph graph = new StructuredGraph.Builder(b.getOptions(), debug).method(toOriginal(methodHandleMethod)).build();
        try (DebugContext.Scope s = debug.scope("IntrinsifyMethodHandles", graph)) {
            graphBuilder.apply(graph);

            /*
             * We do not care about the improved type information from Pi nodes, so we just delete
             * them to simplify our graph.
             */
            for (PiNode pi : graph.getNodes(PiNode.TYPE)) {
                pi.replaceAndDelete(pi.object());
            }

            /*
             * Support for MethodHandle that adapt the input type to a more generic type, i.e., a
             * MethodHandle that does a dynamic type check on a parameter.
             */
            for (UnaryOpLogicNode node : graph.getNodes().filter(UnaryOpLogicNode.class).filter(v -> v instanceof IsNullNode || v instanceof InstanceOfNode)) {
                ValueNode value = node.getValue();
                if (value instanceof ParameterNode) {
                    /*
                     * We just assume that the InstanceOfNode or IsNullNode are used in an If and
                     * the true-successor is actually the branch we want. If that assumption is
                     * wrong, nothing bad happens - we will just continue to report the invocation
                     * as unsupported because the updated stamp for the parameter will not simplify
                     * the graph.
                     */
                    if (node instanceof InstanceOfNode) {
                        InstanceOfNode inst = (InstanceOfNode) node;
                        TypeReference typeRef = inst.type();
                        value.setStamp(new ObjectStamp(typeRef.getType(), typeRef.isExact(), !inst.allowsNull(), false));
                    } else {
                        assert node instanceof IsNullNode;
                        ResolvedJavaType type = value.stamp(NodeView.DEFAULT).javaType(originalProviders.getMetaAccess());
                        value.setStamp(new ObjectStamp(type, false, /* non-null */ true, false));
                    }
                }
            }

            /*
             * The canonicalizer converts unsafe field accesses for get/set method handles back to
             * high-level field load and store nodes.
             */
            new CanonicalizerPhase().apply(graph, new PhaseContext(originalProviders));
            for (FixedGuardNode guard : graph.getNodes(FixedGuardNode.TYPE)) {
                if (guard.next() instanceof AccessFieldNode && guard.condition() instanceof IsNullNode && guard.isNegated() &&
                                ((IsNullNode) guard.condition()).getValue() == ((AccessFieldNode) guard.next()).object()) {
                    /*
                     * Method handles to load and stores fields have null checks. Remove them, since
                     * the null check is implicitly done by the field access.
                     */
                    GraphUtil.removeFixedWithUnusedInputs(guard);
                }
            }

            debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Final intrinisfication graph");

            /*
             * After parsing (and recursive inlining during parsing), the graph must contain only
             * one invocation (and therefore only one MethodCallTargetNode), plus the parameters,
             * constants, start, and return nodes.
             */
            Node singleFunctionality = null;
            ReturnNode singleReturn = null;
            for (Node node : graph.getNodes()) {
                if (node == graph.start() || node instanceof ParameterNode || node instanceof ConstantNode || node instanceof FrameState) {
                    /* Ignore the allowed framework around the nodes we care about. */
                    continue;
                } else if (node instanceof Invoke) {
                    /* We check the MethodCallTargetNode, so we can ignore the invoke. */
                    continue;
                } else if ((node instanceof MethodCallTargetNode || node instanceof LoadFieldNode || node instanceof StoreFieldNode) && singleFunctionality == null) {
                    singleFunctionality = node;
                    continue;
                } else if (node instanceof ReturnNode && singleReturn == null) {
                    singleReturn = (ReturnNode) node;
                    continue;
                }
                throw new UnsupportedFeatureException("Invoke with MethodHandle argument could not be reduced to at most a single call: " + methodHandleMethod.format("%H.%n(%p)"));
            }

            if (singleFunctionality instanceof MethodCallTargetNode) {
                MethodCallTargetNode singleCallTarget = (MethodCallTargetNode) singleFunctionality;
                assert singleReturn.result() == null || singleReturn.result() == singleCallTarget.invoke();
                /*
                 * Replace the originalTarget with the replacementTarget. Note that the
                 * replacementTarget node belongs to a different graph than originalTarget, so we
                 * need to match parameter back to the original graph and allocate a new
                 * MethodCallTargetNode for the original graph.
                 */
                ValueNode[] replacedArguments = new ValueNode[singleCallTarget.arguments().size()];
                for (int i = 0; i < replacedArguments.length; i++) {
                    replacedArguments[i] = lookup(b, methodHandleArguments, singleCallTarget.arguments().get(i));
                }
                b.handleReplacedInvoke(singleCallTarget.invokeKind(), lookup(singleCallTarget.targetMethod()), replacedArguments, false);

            } else if (singleFunctionality instanceof LoadFieldNode) {
                LoadFieldNode fieldLoad = (LoadFieldNode) singleFunctionality;
                b.addPush(b.getInvokeReturnType().getJavaKind(), LoadFieldNode.create(null, lookup(b, methodHandleArguments, fieldLoad.object()), lookup(fieldLoad.field())));

            } else if (singleFunctionality instanceof StoreFieldNode) {
                StoreFieldNode fieldStore = (StoreFieldNode) singleFunctionality;
                b.add(new StoreFieldNode(lookup(b, methodHandleArguments, fieldStore.object()), lookup(fieldStore.field()), lookup(b, methodHandleArguments, fieldStore.value())));

            } else if (singleReturn.result() != null) {
                /* Replace the invocation with he constant result. */
                JavaConstant constantResult = singleReturn.result().asJavaConstant();
                assert b.getInvokeReturnType().getJavaKind() == constantResult.getJavaKind();
                b.addPush(constantResult.getJavaKind(), ConstantNode.forConstant(lookup(constantResult), universeProviders.getMetaAccess()));
            } else {
                /* No invoke and no return value, so nothing to do. */
                assert b.getInvokeReturnType().getJavaKind() == JavaKind.Void;
            }

        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }

    private ValueNode lookup(GraphBuilderContext b, ValueNode[] methodHandleArguments, ValueNode node) {
        if (node.isConstant()) {
            return ConstantNode.forConstant(lookup(node.asJavaConstant()), universeProviders.getMetaAccess(), b.getGraph());
        } else {
            return methodHandleArguments[((ParameterNode) node).index()];
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

    private static ResolvedJavaMethod toOriginal(ResolvedJavaMethod method) {
        if (method instanceof HostedMethod) {
            return ((HostedMethod) method).wrapped.wrapped;
        } else {
            return ((AnalysisMethod) method).wrapped;
        }
    }

    private static ResolvedJavaType toOriginal(ResolvedJavaType type) {
        if (type instanceof HostedType) {
            return ((HostedType) type).getWrapped().getWrapped();
        } else {
            return ((AnalysisType) type).getWrapped();
        }
    }

    private JavaConstant lookup(JavaConstant constant) {
        return aUniverse.lookup(constant);
    }

    private JavaConstant toOriginal(JavaConstant constant) {
        return aUniverse.toHosted(constant);
    }
}
