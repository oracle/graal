/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import com.oracle.graal.api.meta.Constant;
import com.oracle.graal.api.meta.JavaType;
import com.oracle.graal.api.meta.ResolvedJavaField;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.graph.GraalInternalError;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.hotspot.meta.HotSpotResolvedJavaMethod;
import com.oracle.graal.hotspot.meta.HotSpotResolvedObjectType;
import com.oracle.graal.hotspot.meta.HotSpotSignature;
import com.oracle.graal.nodes.CallTargetNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.InvokeNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.java.SelfReplacingMethodCallTargetNode;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.type.GenericStamp.*;
import com.oracle.graal.replacements.nodes.MacroNode;

/**
 * Common base class for method handle invoke nodes.
 */
public abstract class AbstractMethodHandleNode extends MacroNode implements Canonicalizable {

    private static final ResolvedJavaField methodHandleFormField;
    private static final ResolvedJavaField lambdaFormVmentryField;
    private static final ResolvedJavaField memberNameClazzField;
    private static final ResolvedJavaField memberNameVmtargetField;

    // Replacement method data
    private ResolvedJavaMethod replacementTargetMethod;
    private JavaType replacementReturnType;
    @Input private final NodeInputList<ValueNode> replacementArguments;

    /**
     * Search for an instance field with the given name in a class.
     * 
     * @param className name of the class to search in
     * @param fieldName name of the field to be searched
     * @return resolved java field
     * @throws ClassNotFoundException
     */
    private static ResolvedJavaField findFieldInClass(String className, String fieldName) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className);
        ResolvedJavaType type = HotSpotResolvedObjectType.fromClass(clazz);
        ResolvedJavaField[] fields = type.getInstanceFields(false);
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    static {
        try {
            methodHandleFormField = findFieldInClass("java.lang.invoke.MethodHandle", "form");
            lambdaFormVmentryField = findFieldInClass("java.lang.invoke.LambdaForm", "vmentry");
            memberNameClazzField = findFieldInClass("java.lang.invoke.MemberName", "clazz");
            memberNameVmtargetField = findFieldInClass("java.lang.invoke.MemberName", "vmtarget");
        } catch (ClassNotFoundException | SecurityException ex) {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public AbstractMethodHandleNode(Invoke invoke) {
        super(invoke);

        // See if we need to save some replacement method data.
        CallTargetNode callTarget = invoke.callTarget();
        if (callTarget instanceof SelfReplacingMethodCallTargetNode) {
            SelfReplacingMethodCallTargetNode selfReplacingMethodCallTargetNode = (SelfReplacingMethodCallTargetNode) callTarget;
            replacementTargetMethod = selfReplacingMethodCallTargetNode.replacementTargetMethod();
            replacementReturnType = selfReplacingMethodCallTargetNode.replacementReturnType();
            replacementArguments = selfReplacingMethodCallTargetNode.replacementArguments();
        } else {
            // NodeInputList can't be null.
            replacementArguments = new NodeInputList<>(this);
        }
    }

    /**
     * Get the receiver of a MethodHandle.invokeBasic call.
     * 
     * @return the receiver argument node
     */
    private ValueNode getReceiver() {
        return arguments.first();
    }

    /**
     * Get the MemberName argument of a MethodHandle.linkTo* call.
     * 
     * @return the MemberName argument node (which is the last argument)
     */
    private ValueNode getMemberName() {
        return arguments.last();
    }

    /**
     * Used from {@link MethodHandleInvokeBasicNode} to get the target {@link InvokeNode} if the
     * method handle receiver is constant.
     * 
     * @return invoke node for the {@link java.lang.invoke.MethodHandle} target
     */
    protected InvokeNode getInvokeBasicTarget() {
        ValueNode methodHandleNode = getReceiver();
        if (methodHandleNode.isConstant() && !methodHandleNode.isNullConstant()) {
            // Get the data we need from MethodHandle.LambdaForm.MemberName
            Constant methodHandle = methodHandleNode.asConstant();
            Constant lambdaForm = methodHandleFormField.readValue(methodHandle);
            Constant memberName = lambdaFormVmentryField.readValue(lambdaForm);
            return getTargetInvokeNode(memberName);
        }
        return null;
    }

    /**
     * Used from {@link MethodHandleLinkToStaticNode}, {@link MethodHandleLinkToSpecialNode},
     * {@link MethodHandleLinkToVirtualNode}, and {@link MethodHandleLinkToInterfaceNode} to get the
     * target {@link InvokeNode} if the member name argument is constant.
     * 
     * @return invoke node for the member name target
     */
    protected InvokeNode getLinkToTarget() {
        ValueNode memberNameNode = getMemberName();
        if (memberNameNode.isConstant() && !memberNameNode.isNullConstant()) {
            Constant memberName = memberNameNode.asConstant();
            return getTargetInvokeNode(memberName);
        }
        return null;
    }

    /**
     * Helper function to get the {@link InvokeNode} for the vmtarget of a
     * java.lang.invoke.MemberName.
     * 
     * @param memberName constant member name node
     * @return invoke node for the member name target
     */
    private InvokeNode getTargetInvokeNode(Constant memberName) {
        // Get the data we need from MemberName
        Constant clazz = memberNameClazzField.readValue(memberName);
        Constant vmtarget = memberNameVmtargetField.readValue(memberName);

        // Create a method from the vmtarget pointer
        Class<?> c = (Class<?>) clazz.asObject();
        HotSpotResolvedObjectType holderClass = (HotSpotResolvedObjectType) HotSpotResolvedObjectType.fromClass(c);
        HotSpotResolvedJavaMethod targetMethod = HotSpotResolvedJavaMethod.fromMetaspace(vmtarget.asLong());

        // In lambda forms we erase signature types to avoid resolving issues
        // involving class loaders. When we optimize a method handle invoke
        // to a direct call we must cast the receiver and arguments to its
        // actual types.
        HotSpotSignature signature = targetMethod.getSignature();
        final boolean isStatic = Modifier.isStatic(targetMethod.getModifiers());
        final int receiverSkip = isStatic ? 0 : 1;

        // Cast receiver to its type.
        if (!isStatic) {
            JavaType receiverType = holderClass;
            maybeCastArgument(0, receiverType);
        }

        // Cast reference arguments to its type.
        for (int index = 0; index < signature.getParameterCount(false); index++) {
            JavaType parameterType = signature.getParameterType(index, holderClass);
            maybeCastArgument(receiverSkip + index, parameterType);
        }

        // Try to get the most accurate receiver type
        if (this instanceof MethodHandleLinkToVirtualNode || this instanceof MethodHandleLinkToInterfaceNode) {
            ResolvedJavaType receiverType = ObjectStamp.typeOrNull(getReceiver().stamp());
            if (receiverType != null) {
                ResolvedJavaMethod concreteMethod = receiverType.findUniqueConcreteMethod(targetMethod);
                if (concreteMethod != null) {
                    return createTargetInvokeNode(concreteMethod);
                }
            }
        }

        if (targetMethod.canBeStaticallyBound()) {
            return createTargetInvokeNode(targetMethod);
        }

        ResolvedJavaMethod concreteMethod = targetMethod.uniqueConcreteMethod();
        if (concreteMethod != null) {
            return createTargetInvokeNode(concreteMethod);
        }

        return null;
    }

    /**
     * Inserts a node to cast the argument at index to the given type if the given type is more
     * concrete than the argument type.
     * 
     * @param index of the argument to be cast
     * @param type the type the argument should be cast to
     */
    private void maybeCastArgument(int index, JavaType type) {
        if (type instanceof ResolvedJavaType) {
            ResolvedJavaType targetType = (ResolvedJavaType) type;
            if (!targetType.isPrimitive()) {
                ValueNode argument = arguments.get(index);
                ResolvedJavaType argumentType = ObjectStamp.typeOrNull(argument.stamp());
                if (argumentType == null || (argumentType.isAssignableFrom(targetType) && !argumentType.equals(targetType))) {
                    PiNode piNode = graph().unique(new PiNode(argument, StampFactory.declared(targetType)));
                    arguments.set(index, piNode);
                }
            }
        }
    }

    /**
     * Creates an {@link InvokeNode} for the given target method. The {@link CallTargetNode} passed
     * to the InvokeNode is in fact a {@link SelfReplacingMethodCallTargetNode}.
     * 
     * @param targetMethod the method the be called
     * @return invoke node for the member name target
     */
    private InvokeNode createTargetInvokeNode(ResolvedJavaMethod targetMethod) {
        InvokeKind invokeKind = Modifier.isStatic(targetMethod.getModifiers()) ? InvokeKind.Static : InvokeKind.Special;
        JavaType returnType = targetMethod.getSignature().getReturnType(null);

        // MethodHandleLinkTo* nodes have a trailing MemberName argument which
        // needs to be popped.
        ValueNode[] originalArguments = arguments.toArray(new ValueNode[arguments.size()]);
        ValueNode[] targetArguments;
        if (this instanceof MethodHandleInvokeBasicNode) {
            targetArguments = originalArguments;
        } else {
            assert this instanceof MethodHandleLinkToStaticNode || this instanceof MethodHandleLinkToSpecialNode || this instanceof MethodHandleLinkToVirtualNode ||
                            this instanceof MethodHandleLinkToInterfaceNode : this;
            targetArguments = Arrays.copyOfRange(originalArguments, 0, arguments.size() - 1);
        }

        // If there is already replacement information, use that instead.
        MethodCallTargetNode callTarget;
        if (replacementTargetMethod == null) {
            callTarget = new SelfReplacingMethodCallTargetNode(invokeKind, targetMethod, targetArguments, returnType, getTargetMethod(), originalArguments, getReturnType());
        } else {
            ValueNode[] args = replacementArguments.toArray(new ValueNode[replacementArguments.size()]);
            callTarget = new SelfReplacingMethodCallTargetNode(invokeKind, targetMethod, targetArguments, returnType, replacementTargetMethod, args, replacementReturnType);
        }
        graph().add(callTarget);

        // The call target can have a different return type than the invoker,
        // e.g. the target returns an Object but the invoker void. In this case
        // we need to use the stamp of the invoker. Note: always using the
        // invoker's stamp would be wrong because it's a less concrete type
        // (usually java.lang.Object).
        InvokeNode invoke;
        if (stamp() instanceof GenericStamp && ((GenericStamp) stamp()).type() == GenericStampType.Void) {
            invoke = new InvokeNode(callTarget, getBci(), stamp());
        } else {
            invoke = new InvokeNode(callTarget, getBci());
        }
        graph().add(invoke);
        invoke.setStateAfter(stateAfter());
        return invoke;
    }

}
