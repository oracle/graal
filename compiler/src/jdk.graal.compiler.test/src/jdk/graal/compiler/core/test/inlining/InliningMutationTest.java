/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.inlining;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class InliningMutationTest extends GraalCompilerTest {
    @BytecodeParserNeverInline(invokeWithException = true)
    public static int callee() {
        return 0;
    }

    public static int caller() {
        return callee();
    }

    public static int callerCatchingOOME() {
        try {
            return callee();
        } catch (OutOfMemoryError e) {
            return -1;
        }
    }

    private StructuredGraph newGraph(ResolvedJavaMethod method) {
        return new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).method(method).build();
    }

    private MethodCallTargetNode callTarget(StructuredGraph graph, ResolvedJavaMethod method, ValueNode... arguments) {
        return graph.add(new MethodCallTargetNode(InvokeKind.Static, method, arguments, StampPair.createSingle(StampFactory.forKind(JavaKind.Int)), null));
    }

    public int instanceCallee() {
        return 42;
    }

    public static int instanceCaller(InliningMutationTest receiver) {
        return receiver.instanceCallee();
    }

    private InvokeNode addInvoke(StructuredGraph graph, MethodCallTargetNode target) {
        InvokeNode invoke = graph.add(new InvokeNode(target, 0));
        invoke.setStateAfter(graph.add(new FrameState(BytecodeFrame.AFTER_BCI, invoke)));
        invoke.setNext(graph.add(new ReturnNode(invoke)));
        graph.start().setNext(invoke);
        return invoke;
    }

    private InvokeNode addInstanceInvoke(StructuredGraph graph, boolean nonNull) {
        ValueNode receiver = graph.unique(new ParameterNode(0, StampPair.createSingle(nonNull ? StampFactory.objectNonNull() : StampFactory.object())));
        MethodCallTargetNode target = graph.add(new MethodCallTargetNode(InvokeKind.Special, getResolvedJavaMethod("instanceCallee"), new ValueNode[]{receiver},
                        StampPair.createSingle(StampFactory.forKind(JavaKind.Int)), null));
        return addInvoke(graph, target);
    }

    @Test
    public void testNullCheckWithFixedDeopts() {
        StructuredGraph graph = newGraph(getResolvedJavaMethod("instanceCaller"));
        graph.getGraphState().configureExplicitExceptionsNoDeopt();
        InvokeNode invoke = addInstanceInvoke(graph, false);

        ValueNode receiver = InliningUtil.nonNullReceiver(invoke);

        Assert.assertTrue(StampTool.isPointerNonNull(receiver));
        Assert.assertSame(receiver, invoke.callTarget().arguments().get(0));
        Assert.assertTrue(invoke.predecessor() instanceof FixedGuardNode);
        Assert.assertTrue(graph.verify());
    }

    @Test
    public void testNullCheckAfterFSA() {
        StructuredGraph graph = newGraph(getResolvedJavaMethod("instanceCaller"));
        graph.getGraphState().setAfterFSA();
        InvokeNode invoke = addInstanceInvoke(graph, false);

        Assert.assertThrows(GraalError.class, () -> InliningUtil.nonNullReceiver(invoke));
        Assert.assertTrue(graph.getNodes(FixedGuardNode.TYPE).isEmpty());
    }

    @Test
    public void testNonNullReceiverAfterFSA() {
        StructuredGraph graph = newGraph(getResolvedJavaMethod("instanceCaller"));
        graph.getGraphState().setAfterFSA();
        InvokeNode invoke = addInstanceInvoke(graph, true);

        Assert.assertTrue(StampTool.isPointerNonNull(InliningUtil.nonNullReceiver(invoke)));
        Assert.assertTrue(graph.getNodes(FixedGuardNode.TYPE).isEmpty());
    }

    @Test
    public void testInliningFixedDeoptsWithoutGuardLowering() {
        checkInliningGuardsStage(true);
    }

    @Test
    public void testInliningRejectsFloatingGuards() {
        checkInliningGuardsStage(false);
    }

    private void checkInliningGuardsStage(boolean fixedDeopts) {
        StructuredGraph graph = newGraph(getResolvedJavaMethod("caller"));
        graph.getGraphState().configureExplicitExceptionsNoDeopt();
        ResolvedJavaMethod method = getResolvedJavaMethod("callee");
        InvokeNode invoke = addInvoke(graph, callTarget(graph, method));
        StructuredGraph inlinee = newGraph(method);
        if (fixedDeopts) {
            inlinee.getGraphState().setGuardsStage(GuardsStage.FIXED_DEOPTS);
        }
        inlinee.start().setNext(inlinee.add(new ReturnNode(ConstantNode.forInt(42, inlinee))));

        if (fixedDeopts) {
            InliningUtil.inline(invoke, inlinee, false, method);
            Assert.assertTrue(graph.getNodes().filter(InvokeNode.class).isEmpty());
            Assert.assertEquals(42, graph.getNodes(ReturnNode.TYPE).first().result().asJavaConstant().asInt());
        } else {
            Assert.assertThrows(GraalError.class, () -> InliningUtil.inline(invoke, inlinee, false, method));
            Assert.assertTrue(invoke.isAlive());
        }
        Assert.assertTrue(graph.verify());
    }

    @Test
    public void testDeletedExceptionalReturn() {
        checkDeletedExceptionalReturn(InliningUtil.NoReturnAction);
    }

    @Test
    public void testReturnActionReceivesOnlyLiveReturns() {
        checkDeletedExceptionalReturn(new InliningUtil.InlineeReturnAction() {
            @Override
            public List<ReturnNode> processInlineeReturns(List<ReturnNode> returns) {
                Assert.assertEquals(1, returns.size());
                Assert.assertTrue(returns.get(0).isAlive());
                return returns;
            }
        });
    }

    private void checkDeletedExceptionalReturn(InliningUtil.InlineeReturnAction returnAction) {
        StructuredGraph graph = parseEager("caller", StructuredGraph.AllowAssumptions.YES);
        Invoke invoke = graph.getNodes(MethodCallTargetNode.TYPE).first().invoke();
        if (invoke instanceof InvokeWithExceptionNode throwingInvoke) {
            invoke = throwingInvoke.replaceWithInvoke();
        }
        ResolvedJavaMethod method = getResolvedJavaMethod("callee");
        StructuredGraph inlinee = newGraph(method);
        ExceptionObjectNode exception = inlinee.add(new ExceptionObjectNode(getMetaAccess()));
        exception.setStateAfter(inlinee.add(new FrameState(BytecodeFrame.AFTER_EXCEPTION_BCI, exception)));
        exception.setNext(inlinee.add(new ReturnNode(ConstantNode.forInt(-1, inlinee))));
        InvokeWithExceptionNode inner = inlinee.add(new InvokeWithExceptionNode(callTarget(inlinee, method), exception, 0));
        inner.setStateAfter(inlinee.add(new FrameState(BytecodeFrame.AFTER_BCI, inner)));
        BeginNode normal = inlinee.add(new BeginNode());
        normal.setNext(inlinee.add(new ReturnNode(inner)));
        inner.setNext(normal);
        inlinee.start().setNext(inner);

        InliningUtil.inline(invoke, inlinee, false, method, "test", "test", returnAction);

        Assert.assertEquals(1, graph.getNodes(ReturnNode.TYPE).count());
        Assert.assertFalse(graph.getNodes().filter(InvokeWithExceptionNode.class).isNotEmpty());
        Assert.assertTrue(graph.verify());
    }

    @Test
    public void testInvokeConversionPreservesCallSiteMetadata() {
        StructuredGraph graph = parseEager("callerCatchingOOME", StructuredGraph.AllowAssumptions.YES);
        InvokeWithExceptionNode invoke = graph.getNodes().filter(InvokeWithExceptionNode.class).first();
        Assert.assertNotNull(invoke);
        Assert.assertTrue(invoke.isInOOMETry());
        invoke.setPolymorphic(true);
        invoke.setInlineControl(Invoke.InlineControl.BytecodesOnly);
        ValueNode classInit = ConstantNode.forInt(1, graph);
        invoke.setClassInit(classInit);
        FrameState stateAfter = invoke.stateAfter();

        InvokeNode replacement = invoke.replaceWithNonThrowing();

        Assert.assertTrue(replacement.isInOOMETry());
        Assert.assertTrue(replacement.isPolymorphic());
        Assert.assertEquals(Invoke.InlineControl.BytecodesOnly, replacement.getInlineControl());
        Assert.assertSame(stateAfter, replacement.stateAfter());
        Assert.assertSame(classInit, replacement.classInit());
        Assert.assertTrue(graph.verify());
    }
}
