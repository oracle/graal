/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.hosted;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class JNIJavaCallWrapperMethodSupport {
    /**
     * Creates the nodes for a JNI {@code NewObject} call.
     * 
     * <ul>
     * <li>An allocation for the receiver</li>
     * <li>An invocation of the constructor method, with the newly created receiver</li>
     * <li>Exception handling of any possible constructor exceptions</li>
     * <li>Synthetic replacement of the return value as the newly allocated object</li>
     * </ul>
     * 
     * @param kit Graph building kit
     * @param invokeMethod Constructor method to invoke after allocation
     * @param state Framestate builder
     * @param args Arguments passed to the constructor, starting at position 1. The argument at
     *            position 0 (the receiver position) can have any value. The arguments should
     *            already have been type-checked dynamically.
     * 
     * @return A node for the newly allocated object or for the exception thrown by the constructor.
     */
    public ValueNode createNewObjectCall(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, FrameStateBuilder state, ValueNode... args) {
        assert invokeMethod.isConstructor() : "Cannot create a NewObject call to the non-constructor method " + invokeMethod;

        /* Receiver allocation */
        ResolvedJavaType receiverClass = invokeMethod.getDeclaringClass();
        AbstractNewObjectNode createdReceiver = createNewInstance(kit, receiverClass, true);

        /*
         * Constructor invocation, with exception handling. We can ignore the value of the invoke
         * node as String constructors have return type void.
         */
        int bci = kit.bci();
        args[0] = createdReceiver;
        startInvokeWithException(kit, invokeMethod, InvokeKind.Special, state, bci, args);
        AbstractMergeNode merge = kit.endInvokeWithException();
        merge.setStateAfter(state.create(bci, merge));

        /* Synthetic replacement of return value */
        Stamp objectStamp = StampFactory.forDeclaredType(null, receiverClass, true).getTrustedStamp();
        ValueNode exceptionValue = kit.unique(ConstantNode.defaultForKind(JavaKind.Object));
        return kit.getGraph().addWithoutUnique(new ValuePhiNode(objectStamp, merge, new ValueNode[]{createdReceiver, exceptionValue}));
    }

    protected AbstractNewObjectNode createNewInstance(JNIGraphKit kit, ResolvedJavaType type, boolean fillContents) {
        return kit.append(new NewInstanceNode(type, fillContents));
    }

    /**
     * Creates the nodes for a JNI {@code Call<Type>Method} call.
     *
     * @param kit Graph building kit
     * @param invokeMethod Method to invoke
     * @param invokeKind Type of invocation
     * @param state Framestate builder
     * @param args Arguments passed to the method. If the method takes a receiver, the receiver
     *            should be included in these arguments. The arguments (and receiver) should already
     *            have been type-checked dynamically.
     *
     * @return A node representing the return value of the invoke. For constructors, this node is
     *         the receiver; for other methods with a return type of {@link JavaKind#Void void},
     *         this method returns {@code null}.
     */
    public ValueNode createCallTypeMethodCall(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, InvokeKind invokeKind, FrameStateBuilder state, ValueNode... args) {
        int bci = kit.bci();
        InvokeWithExceptionNode invoke = startInvokeWithException(kit, invokeMethod, invokeKind, state, bci, args);
        AbstractMergeNode invokeMerge = kit.endInvokeWithException();

        if (invoke.getStackKind() == JavaKind.Void && !invokeMethod.isConstructor()) {
            invokeMerge.setStateAfter(state.create(bci, invokeMerge));
            return null;
        }

        /* Place return value in merge FrameState */
        ValueNode successValue = invokeMethod.isConstructor() ? args[0] : invoke;
        ValueNode exceptionValue = kit.unique(ConstantNode.defaultForKind(successValue.getStackKind()));
        ValueNode[] inputs = {successValue, exceptionValue};
        ValueNode returnValue = kit.getGraph().addWithoutUnique(new ValuePhiNode(successValue.stamp(NodeView.DEFAULT), invokeMerge, inputs));
        JavaKind returnKind = returnValue.getStackKind();
        state.push(returnKind, returnValue);
        invokeMerge.setStateAfter(state.create(bci, invokeMerge));
        state.pop(returnKind);
        return returnValue;
    }

    protected InvokeWithExceptionNode startInvokeWithException(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, InvokeKind kind, FrameStateBuilder state, int bci,
                    ValueNode... args) {
        ValueNode formerPendingException = kit.getAndClearPendingException();
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(invokeMethod, kind, state, bci, args);

        kit.noExceptionPart(); // no new exception was thrown, restore the formerly pending one
        kit.setPendingException(formerPendingException);

        kit.exceptionPart();
        ExceptionObjectNode exceptionObject = kit.exceptionObject();
        kit.setPendingException(exceptionObject);

        return invoke;
    }
}
