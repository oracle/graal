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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo
public class MethodCallTargetNode extends CallTargetNode implements IterableNodeType, Simplifiable {
    public static final NodeClass<MethodCallTargetNode> TYPE = NodeClass.get(MethodCallTargetNode.class);
    protected final JavaType returnType;

    public MethodCallTargetNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, JavaType returnType) {
        this(TYPE, invokeKind, targetMethod, arguments, returnType);
    }

    protected MethodCallTargetNode(NodeClass<? extends MethodCallTargetNode> c, InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, JavaType returnType) {
        super(c, arguments, targetMethod, invokeKind);
        this.returnType = returnType;
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

    public Kind returnKind() {
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
            if (assumptions != null) {
                ResolvedJavaType uniqueConcreteType = type.findUniqueConcreteSubtype();
                if (uniqueConcreteType != null) {
                    ResolvedJavaMethod methodFromUniqueType = uniqueConcreteType.resolveConcreteMethod(targetMethod, contextType);
                    if (methodFromUniqueType != null) {
                        assumptions.recordConcreteSubtype(type, uniqueConcreteType);
                        return methodFromUniqueType;
                    }
                }

                ResolvedJavaMethod uniqueConcreteMethod = type.findUniqueConcreteMethod(targetMethod);
                if (uniqueConcreteMethod != null) {
                    assumptions.recordConcreteMethod(targetMethod, type, uniqueConcreteMethod);
                    return uniqueConcreteMethod;
                }
            }
        }

        return null;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        // attempt to devirtualize the call
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
                tryCheckCastSingleImplementor(receiver, declaredReceiverType);
            }

            if (receiver instanceof UncheckedInterfaceProvider) {
                UncheckedInterfaceProvider uncheckedInterfaceProvider = (UncheckedInterfaceProvider) receiver;
                Stamp uncheckedStamp = uncheckedInterfaceProvider.uncheckedStamp();
                if (uncheckedStamp != null) {
                    ResolvedJavaType uncheckedReceiverType = StampTool.typeOrNull(uncheckedStamp);
                    if (uncheckedReceiverType.isInterface()) {
                        tryCheckCastSingleImplementor(receiver, uncheckedReceiverType);
                    }
                }
            }
        }
    }

    private void tryCheckCastSingleImplementor(ValueNode receiver, ResolvedJavaType declaredReceiverType) {
        ResolvedJavaType singleImplementor = declaredReceiverType.getSingleImplementor();
        if (singleImplementor != null && !singleImplementor.equals(declaredReceiverType)) {
            ResolvedJavaMethod singleImplementorMethod = singleImplementor.resolveMethod(targetMethod(), invoke().getContextType(), true);
            if (singleImplementorMethod != null) {
                assert graph().getGuardsStage().allowsFloatingGuards() : "Graph already fixed!";
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
                LogicNode condition = graph().unique(new InstanceOfNode(singleImplementor, receiver, getProfile()));
                GuardNode guard = graph().unique(
                                new GuardNode(condition, AbstractBeginNode.prevBegin(invoke().asNode()), DeoptimizationReason.OptimizedTypeCheckViolated, DeoptimizationAction.InvalidateRecompile,
                                                false, JavaConstant.NULL_POINTER));
                PiNode piNode = graph().unique(new PiNode(receiver, StampFactory.declaredNonNull(singleImplementor), guard));
                arguments().set(0, piNode);
                setInvokeKind(InvokeKind.Virtual);
                setTargetMethod(singleImplementorMethod);
            }
        }
    }

    private JavaTypeProfile getProfile() {
        assert !isStatic();
        if (receiver() instanceof TypeProfileProxyNode) {
            // get profile from TypeProfileProxy
            return ((TypeProfileProxyNode) receiver()).getProfile();
        }
        // get profile from invoke()
        ProfilingInfo profilingInfo = invoke().getContextMethod().getProfilingInfo();
        return profilingInfo.getTypeProfile(invoke().bci());
    }

    @Override
    public Stamp returnStamp() {
        Kind returnKind = targetMethod().getSignature().getReturnKind();
        if (returnKind == Kind.Object && returnType instanceof ResolvedJavaType) {
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
