/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.java;

import com.oracle.graal.compiler.common.type.CheckedJavaType;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.IterableNodeType;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Simplifiable;
import com.oracle.graal.graph.spi.SimplifierTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodeinfo.Verbosity;
import com.oracle.graal.nodes.CallTargetNode;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.UncheckedInterfaceProvider;
import com.oracle.graal.nodes.type.StampTool;

@NodeInfo
public class MethodCallTargetNode extends CallTargetNode implements IterableNodeType, Simplifiable {
    public static final NodeClass<MethodCallTargetNode> TYPE = NodeClass.create(MethodCallTargetNode.class);
    protected final JavaType returnType;
    protected JavaTypeProfile profile;

    public MethodCallTargetNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, JavaType returnType, JavaTypeProfile profile) {
        this(TYPE, invokeKind, targetMethod, arguments, returnType, profile);
    }

    protected MethodCallTargetNode(NodeClass<? extends MethodCallTargetNode> c, InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, JavaType returnType,
                    JavaTypeProfile profile) {
        super(c, arguments, targetMethod, invokeKind);
        this.returnType = returnType;
        this.profile = profile;
    }

    /**
     * Gets the instruction that produces the receiver object for this invocation, if any.
     *
     * @return the instruction that produces the receiver object for this invocation if any,
     *         {@code null} if this invocation does not take a receiver object
     */
    public ValueNode receiver() {
        return isStatic() ? null : arguments().get(0);
    }

    /**
     * Checks whether this is an invocation of a static method.
     *
     * @return {@code true} if the invocation is a static invocation
     */
    public boolean isStatic() {
        return invokeKind() == InvokeKind.Static;
    }

    public JavaKind returnKind() {
        return targetMethod().getSignature().getReturnKind();
    }

    public Invoke invoke() {
        return (Invoke) this.usages().first();
    }

    @Override
    public boolean verify() {
        assert getUsageCount() <= 1 : "call target may only be used by a single invoke";
        for (Node n : usages()) {
            assertTrue(n instanceof Invoke, "call target can only be used from an invoke (%s)", n);
        }
        if (invokeKind().isDirect()) {
            assertTrue(targetMethod().isConcrete(), "special calls or static calls are only allowed for concrete methods (%s)", targetMethod());
        }
        if (invokeKind() == InvokeKind.Static) {
            assertTrue(targetMethod().isStatic(), "static calls are only allowed for static methods (%s)", targetMethod());
        } else {
            assertFalse(targetMethod().isStatic(), "static calls are only allowed for non-static methods (%s)", targetMethod());
        }
        return super.verify();
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Long) {
            return super.toString(Verbosity.Short) + "(" + targetMethod() + ")";
        } else {
            return super.toString(verbosity);
        }
    }

    public static ResolvedJavaMethod findSpecialCallTarget(InvokeKind invokeKind, ValueNode receiver, ResolvedJavaMethod targetMethod, ResolvedJavaType contextType) {
        if (invokeKind.isDirect()) {
            return null;
        }

        // check for trivial cases (e.g. final methods, nonvirtual methods)
        if (targetMethod.canBeStaticallyBound()) {
            return targetMethod;
        }

        ResolvedJavaType type = StampTool.typeOrNull(receiver);
        if (type == null && invokeKind == InvokeKind.Virtual) {
            // For virtual calls, we are guaranteed to receive a correct receiver type.
            type = targetMethod.getDeclaringClass();
        }

        if (type != null) {
            /*
             * either the holder class is exact, or the receiver object has an exact type, or it's
             * an array type
             */
            ResolvedJavaMethod resolvedMethod = type.resolveConcreteMethod(targetMethod, contextType);
            if (resolvedMethod != null && (resolvedMethod.canBeStaticallyBound() || StampTool.isExactType(receiver) || type.isArray())) {
                return resolvedMethod;
            }

            Assumptions assumptions = receiver.graph().getAssumptions();
            AssumptionResult<ResolvedJavaType> leafConcreteSubtype = type.findLeafConcreteSubtype();
            if (leafConcreteSubtype != null && leafConcreteSubtype.canRecordTo(assumptions)) {
                ResolvedJavaMethod methodFromUniqueType = leafConcreteSubtype.getResult().resolveConcreteMethod(targetMethod, contextType);
                if (methodFromUniqueType != null) {
                    leafConcreteSubtype.recordTo(assumptions);
                    return methodFromUniqueType;
                }
            }

            AssumptionResult<ResolvedJavaMethod> uniqueConcreteMethod = type.findUniqueConcreteMethod(targetMethod);
            if (uniqueConcreteMethod != null && uniqueConcreteMethod.canRecordTo(assumptions)) {
                uniqueConcreteMethod.recordTo(assumptions);
                return uniqueConcreteMethod.getResult();
            }
        }

        return null;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        // attempt to devirtualize the call
        if (invoke().getContextMethod() == null) {
            // avoid invokes that have placeholder bcis: they do not have a valid contextType
            assert (invoke().stateAfter() != null && BytecodeFrame.isPlaceholderBci(invoke().stateAfter().bci)) || BytecodeFrame.isPlaceholderBci(invoke().stateDuring().bci);
            return;
        }
        ResolvedJavaType contextType = (invoke().stateAfter() == null && invoke().stateDuring() == null) ? null : invoke().getContextType();
        ResolvedJavaMethod specialCallTarget = findSpecialCallTarget(invokeKind, receiver(), targetMethod, contextType);
        if (specialCallTarget != null) {
            this.setTargetMethod(specialCallTarget);
            setInvokeKind(InvokeKind.Special);
            return;
        }

        if (invokeKind().isIndirect() && invokeKind().isInterface()) {

            // check if the type of the receiver can narrow the result
            ValueNode receiver = receiver();

            // try to turn a interface call into a virtual call
            ResolvedJavaType declaredReceiverType = targetMethod().getDeclaringClass();
            /*
             * We need to check the invoke kind to avoid recursive simplification for virtual
             * interface methods calls.
             */
            if (declaredReceiverType.isInterface()) {
                tryCheckCastSingleImplementor(graph().getAssumptions(), receiver, declaredReceiverType);
            }

            if (receiver instanceof UncheckedInterfaceProvider) {
                UncheckedInterfaceProvider uncheckedInterfaceProvider = (UncheckedInterfaceProvider) receiver;
                Stamp uncheckedStamp = uncheckedInterfaceProvider.uncheckedStamp();
                if (uncheckedStamp != null) {
                    ResolvedJavaType uncheckedReceiverType = StampTool.typeOrNull(uncheckedStamp);
                    if (uncheckedReceiverType.isInterface()) {
                        tryCheckCastSingleImplementor(graph().getAssumptions(), receiver, uncheckedReceiverType);
                    }
                }
            }
        }
    }

    private void tryCheckCastSingleImplementor(Assumptions assumptions, ValueNode receiver, ResolvedJavaType declaredReceiverType) {
        if (assumptions == null) {
            /*
             * Even though we are not registering an assumption (see comment below), the
             * optimization is only valid when speculative optimizations are enabled.
             */
            return;
        }

        ResolvedJavaType singleImplementor = declaredReceiverType.getSingleImplementor();
        if (singleImplementor != null && !singleImplementor.equals(declaredReceiverType)) {
            ResolvedJavaMethod singleImplementorMethod = singleImplementor.resolveMethod(targetMethod(), invoke().getContextType());
            if (singleImplementorMethod != null) {
                /**
                 * We have an invoke on an interface with a single implementor. We can replace this
                 * with an invoke virtual.
                 *
                 * To do so we need to ensure two properties: 1) the receiver must implement the
                 * interface (declaredReceiverType). The verifier does not prove this so we need a
                 * dynamic check. 2) we need to ensure that there is still only one implementor of
                 * this interface, i.e. that we are calling the right method. We could do this with
                 * an assumption but as we need an instanceof check anyway we can verify both
                 * properties by checking of the receiver is an instance of the single implementor.
                 */
                LogicNode condition = graph().unique(InstanceOfNode.create(CheckedJavaType.create(assumptions, singleImplementor), receiver, getProfile()));
                FixedGuardNode guard = graph().add(new FixedGuardNode(condition, DeoptimizationReason.OptimizedTypeCheckViolated, DeoptimizationAction.InvalidateRecompile, false));
                graph().addBeforeFixed(invoke().asNode(), guard);
                PiNode piNode = graph().unique(new PiNode(receiver, StampFactory.declaredNonNull(singleImplementor), guard));
                arguments().set(0, piNode);
                setInvokeKind(InvokeKind.Virtual);
                setTargetMethod(singleImplementorMethod);
            }
        }
    }

    public JavaTypeProfile getProfile() {
        return profile;
    }

    @Override
    public Stamp returnStamp() {
        JavaKind returnKind = targetMethod().getSignature().getReturnKind();
        if (returnKind == JavaKind.Object && returnType instanceof ResolvedJavaType) {
            return StampFactory.declared((ResolvedJavaType) returnType);
        } else {
            return StampFactory.forKind(returnKind);
        }
    }

    public JavaType returnType() {
        return returnType;
    }

    @Override
    public String targetName() {
        if (targetMethod() == null) {
            return "??Invalid!";
        }
        return targetMethod().format("%h.%n");
    }

    public static MethodCallTargetNode find(StructuredGraph graph, ResolvedJavaMethod method) {
        for (MethodCallTargetNode target : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (target.targetMethod().equals(method)) {
                return target;
            }
        }
        return null;
    }
}
