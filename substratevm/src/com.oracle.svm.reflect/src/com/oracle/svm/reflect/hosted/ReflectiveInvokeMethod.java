/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.SubstrateIntrinsicGraphBuilder;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.reflect.SubstrateMethodAccessor;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.code.NonBytecodeStaticMethod;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ReflectiveInvokeMethod extends NonBytecodeStaticMethod {

    private final Executable method;

    public ReflectiveInvokeMethod(String name, ResolvedJavaMethod prototype, Executable method) {
        super(name, prototype.getDeclaringClass(), prototype.getSignature(), prototype.getConstantPool());
        this.method = method;
    }

    /**
     * Builds the graph that is invoked via {@link SubstrateMethodAccessor}. To save code size, both
     * the {@link SubstrateMethodAccessor#invoke "regular"} and the
     * {@link SubstrateMethodAccessor#invokeSpecial} invocations are done via the same generated
     * graph. The first parameter decides which invocation is done.
     */
    @Override
    public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod m, HostedProviders providers, Purpose purpose) {
        ReflectionGraphKit graphKit = new ReflectionGraphKit(ctx, providers, m);

        ValueNode forceSpecialInvoke = graphKit.loadLocal(0, JavaKind.Int);
        ValueNode receiver = graphKit.loadLocal(1, JavaKind.Object);
        ValueNode argumentArray = graphKit.loadLocal(2, JavaKind.Object);
        /* Clear all locals, so that they are not alive and spilled at method calls. */
        graphKit.getFrameState().clearLocals();

        ResolvedJavaMethod targetMethod = providers.getMetaAccess().lookupJavaMethod(method);
        if (targetMethod.isStatic() || targetMethod.isConstructor()) {
            graphKit.emitEnsureInitializedCall(targetMethod.getDeclaringClass());
        }
        if (targetMethod.isConstructor()) {
            /*
             * For a constructor, we invoke a synthetic static factory method that combines both the
             * allocation and the constructor invocation.
             */
            targetMethod = FactoryMethodSupport.singleton().lookup((UniverseMetaAccess) providers.getMetaAccess(), targetMethod, false);
        }

        Class<?>[] argTypes = method.getParameterTypes();
        int receiverOffset = targetMethod.isStatic() ? 0 : 1;
        ValueNode[] args = new ValueNode[argTypes.length + receiverOffset];
        if (!targetMethod.isStatic()) {
            /*
             * The specification explicitly demands a NullPointerException and not a
             * IllegalArgumentException when the receiver of a non-static method is null
             */
            ValueNode receiverNonNull = graphKit.maybeCreateExplicitNullCheck(receiver);

            args[0] = graphKit.startInstanceOf(receiverNonNull, targetMethod.getDeclaringClass(), true, true);
            graphKit.elsePart();
            graphKit.branchToIllegalArgumentException();
            graphKit.endIf();
        }

        graphKit.fillArgsArray(argumentArray, receiverOffset, args, argTypes);

        InvokeKind invokeKind;
        assert !targetMethod.isConstructor() : "Constructors are already rewritten to static factory methods";
        if (targetMethod.isStatic()) {
            invokeKind = InvokeKind.Static;
        } else if (targetMethod.isInterface()) {
            invokeKind = InvokeKind.Interface;
        } else if (targetMethod.canBeStaticallyBound()) {
            invokeKind = InvokeKind.Special;
        } else {
            invokeKind = InvokeKind.Virtual;
        }

        List<InvokeWithExceptionNode> invokes = new ArrayList<>();
        if (invokeKind.isIndirect()) {
            /*
             * The graphs are generated before the static analysis is finished. At that time, only
             * final methods are known to be statically bound. After the static analysis, many more
             * methods can be statically bound. To avoid two identical invokeSpecial invokes for
             * such cases, we add a CanBeStaticallyBoundNode to the condition. It is replaced with
             * "true" when the method can be statically bound after static analysis, effectively
             * removing the whole IfNode and the second invoke node.
             */
            LogicNode canBeStaticallyBoundCondition = graphKit.getGraph().unique(new CanBeStaticallyBoundNode(targetMethod));
            LogicNode forceSpecialInvokeCondition = graphKit.append(IntegerEqualsNode.create(forceSpecialInvoke, ConstantNode.forBoolean(true, graphKit.getGraph()), NodeView.DEFAULT));
            LogicNode condition = LogicNode.or(canBeStaticallyBoundCondition, forceSpecialInvokeCondition, BranchProbabilityNode.NOT_LIKELY_PROFILE);
            graphKit.startIf(condition, BranchProbabilityNode.NOT_LIKELY_PROFILE);

            graphKit.thenPart();
            if (targetMethod.isAbstract()) {
                graphKit.branchToIllegalArgumentException();
            } else {
                InvokeWithExceptionNode specialInvoke = graphKit.createJavaCallWithException(InvokeKind.Special, targetMethod, args);
                invokes.add(specialInvoke);
                graphKit.exceptionPart();
                graphKit.branchToInvocationTargetException(graphKit.exceptionObject());
                graphKit.endInvokeWithException();
            }

            graphKit.elsePart();
        }

        InvokeWithExceptionNode regularInvoke = graphKit.createJavaCallWithException(invokeKind, targetMethod, args);
        invokes.add(regularInvoke);
        graphKit.exceptionPart();
        graphKit.branchToInvocationTargetException(graphKit.exceptionObject());
        graphKit.endInvokeWithException();

        AbstractMergeNode merge = invokeKind.isIndirect() ? graphKit.endIf() : null;

        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        ValueNode returnValue;
        if (returnKind == JavaKind.Void) {
            returnValue = graphKit.createObject(null);
        } else {
            returnValue = graphKit.createPhi(invokes, merge);
            if (returnKind.isPrimitive()) {
                ResolvedJavaType boxedRetType = graphKit.getMetaAccess().lookupJavaType(returnKind.toBoxedJavaClass());
                returnValue = graphKit.createBoxing(returnValue, returnKind, boxedRetType);
            }
        }
        graphKit.createReturn(returnValue, JavaKind.Object);

        graphKit.emitIllegalArgumentException(method, invokeKind == InvokeKind.Static ? null : receiver, argumentArray);
        graphKit.emitInvocationTargetException();

        for (InvokeWithExceptionNode invoke : invokes) {
            if (invoke.getInvokeKind().isDirect()) {
                InvocationPlugin invocationPlugin = providers.getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(targetMethod);
                if (invocationPlugin != null && !invocationPlugin.inlineOnly()) {
                    /*
                     * The BytecodeParser applies invocation plugins directly during bytecode
                     * parsing. We cannot do that because GraphKit is not a GraphBuilderContext. To
                     * get as close as possible to the BytecodeParser behavior, we create a new
                     * graph for the intrinsic and inline it immediately.
                     */
                    Bytecode code = new ResolvedJavaMethodBytecode(targetMethod);
                    StructuredGraph intrinsicGraph = new SubstrateIntrinsicGraphBuilder(graphKit.getOptions(), graphKit.getDebug(), providers, code).buildGraph(invocationPlugin);
                    if (intrinsicGraph != null) {
                        InliningUtil.inline(invoke, intrinsicGraph, false, targetMethod);
                    }
                }
            }
        }

        return graphKit.finalizeGraph();
    }
}

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
final class CanBeStaticallyBoundNode extends LogicNode implements Canonicalizable {
    public static final NodeClass<CanBeStaticallyBoundNode> TYPE = NodeClass.create(CanBeStaticallyBoundNode.class);

    private final ResolvedJavaMethod method;

    protected CanBeStaticallyBoundNode(ResolvedJavaMethod method) {
        super(TYPE);
        this.method = method;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (method.canBeStaticallyBound()) {
            /* Success: the static analysis found the method to be statically bound. */
            return LogicConstantNode.tautology();
        } else if (method instanceof SharedMethod) {
            /*
             * We are after the static analysis and the method can still not be statically bound.
             * Eliminate this node, but canonicalizing to "false" keeps the dynamic check in place
             * in the generated Graal graph.
             */
            return LogicConstantNode.contradiction();
        } else {
            /* Static analysis is still running. */
            return this;
        }
    }
}
