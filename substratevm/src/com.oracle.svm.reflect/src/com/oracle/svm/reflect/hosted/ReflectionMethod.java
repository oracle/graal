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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;

import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.util.ExceptionHelpers;
import com.oracle.svm.hosted.code.NonBytecodeStaticMethod;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class ReflectionMethod extends NonBytecodeStaticMethod {

    public ReflectionMethod(String name, ResolvedJavaMethod prototype) {
        super(name, prototype.getDeclaringClass(), prototype.getSignature(), prototype.getConstantPool());
    }

    protected static void throwFailedCast(HostedGraphKit graphKit, ResolvedJavaType expectedType, ValueNode actual) {
        ResolvedJavaMethod throwFailedCast = graphKit.findMethod(ExceptionHelpers.class, "throwFailedCast", true);
        JavaConstant expected = graphKit.getConstantReflection().asJavaClass(expectedType);
        ValueNode expectedNode = graphKit.createConstant(expected, JavaKind.Object);

        graphKit.createJavaCallWithExceptionAndUnwind(CallTargetNode.InvokeKind.Static, throwFailedCast, expectedNode, actual);
        graphKit.append(new LoweredDeadEndNode());
    }

    protected static ValueNode createCheckcast(HostedGraphKit graphKit, ValueNode value, ResolvedJavaType type, boolean nonNull) {
        TypeReference typeRef = TypeReference.createTrusted(graphKit.getAssumptions(), type);
        LogicNode condition;
        if (nonNull) {
            condition = graphKit.append(InstanceOfNode.create(typeRef, value));
        } else {
            condition = graphKit.append(InstanceOfNode.createAllowNull(typeRef, value, null, null));
        }

        graphKit.startIf(condition, BranchProbabilityNode.FAST_PATH_PROFILE);
        graphKit.thenPart();

        PiNode ret = graphKit.createPiNode(value, StampFactory.object(typeRef, nonNull));

        graphKit.elsePart();

        throwFailedCast(graphKit, type, value);

        graphKit.endIf();

        return ret;
    }

    protected static void fillArgsArray(HostedGraphKit graphKit, ValueNode argumentArray, int receiverOffset, ValueNode[] args, Class<?>[] argTypes) {
        /*
         * The length of the args array at run time must be the same as the length of argTypes.
         * Unless the length of argTypes is 0: in that case, null is allowed to be passed in at run
         * time too.
         */
        LogicNode argsNullCondition = graphKit.append(IsNullNode.create(argumentArray));
        graphKit.startIf(argsNullCondition, BranchProbabilityNode.SLOW_PATH_PROFILE);
        graphKit.thenPart();
        if (argTypes.length == 0) {
            /* No arguments, so null is an allowed value. */
        } else {
            throwIllegalArgumentException(graphKit, "wrong number of arguments");
        }
        graphKit.elsePart();
        PiNode argumentArrayNonNull = graphKit.createPiNode(argumentArray, StampFactory.objectNonNull());

        ValueNode argsLength = graphKit.append(ArrayLengthNode.create(argumentArrayNonNull, graphKit.getConstantReflection()));
        LogicNode argsLengthCondition = graphKit.append(IntegerEqualsNode.create(argsLength, ConstantNode.forInt(argTypes.length), NodeView.DEFAULT));
        graphKit.startIf(argsLengthCondition, BranchProbabilityNode.FAST_PATH_PROFILE);
        graphKit.thenPart();

        for (int i = 0; i < argTypes.length; i++) {
            ValueNode arg = graphKit.createLoadIndexed(argumentArrayNonNull, i, JavaKind.Object);
            ResolvedJavaType argType = graphKit.getMetaAccess().lookupJavaType(argTypes[i]);
            JavaKind argKind = graphKit.asKind(argType);
            if (argKind.isPrimitive()) {
                arg = createCheckcast(graphKit, arg, graphKit.getMetaAccess().lookupJavaType(argKind.toBoxedJavaClass()), true);
                arg = graphKit.createUnboxing(arg, argKind, graphKit.getMetaAccess());
            } else {
                arg = createCheckcast(graphKit, arg, argType, false);
            }

            args[i + receiverOffset] = arg;
        }

        graphKit.elsePart();
        throwIllegalArgumentException(graphKit, "wrong number of arguments");
        graphKit.endIf();

        AbstractMergeNode merge = graphKit.endIf();
        if (merge != null) {
            /* When argTypes.length == 0 there is an actual merge that needs a state. */
            merge.setStateAfter(graphKit.getFrameState().create(graphKit.bci(), merge));
        }
    }

    protected static void throwIllegalArgumentException(HostedGraphKit graphKit, String message) {
        ResolvedJavaMethod throwIllegalArgumentException = graphKit.findMethod(ExceptionHelpers.class, "throwIllegalArgumentException", true);
        JavaConstant msg = graphKit.getConstantReflection().forString(message);
        ValueNode msgNode = graphKit.createConstant(msg, JavaKind.Object);

        graphKit.createJavaCallWithExceptionAndUnwind(CallTargetNode.InvokeKind.Static, throwIllegalArgumentException, msgNode);
        graphKit.append(new LoweredDeadEndNode());
    }
}
