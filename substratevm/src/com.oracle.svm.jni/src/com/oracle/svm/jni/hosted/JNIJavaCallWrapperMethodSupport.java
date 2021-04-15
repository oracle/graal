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
     * Builds the object allocation for a JNI {@code NewObject} call, returning a node that contains
     * the created object or for {@code null} when an exception occurred (in which case the
     * exception becomes a JNI pending exception).
     */
    public ValueNode createNewObjectCall(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, FrameStateBuilder state, ValueNode... args) {
        assert invokeMethod.isConstructor() : "Cannot create a NewObject call to the non-constructor method " + invokeMethod;

        ResolvedJavaType receiverClass = invokeMethod.getDeclaringClass();
        AbstractNewObjectNode createdReceiver = createNewInstance(kit, receiverClass, true);

        int bci = kit.bci();
        args[0] = createdReceiver;
        startInvokeWithRetainedException(kit, invokeMethod, InvokeKind.Special, state, bci, args);
        AbstractMergeNode merge = kit.endInvokeWithException();
        merge.setStateAfter(state.create(bci, merge));

        Stamp objectStamp = StampFactory.forDeclaredType(null, receiverClass, true).getTrustedStamp();
        ValueNode exceptionValue = kit.unique(ConstantNode.defaultForKind(JavaKind.Object));
        return kit.getGraph().addWithoutUnique(new ValuePhiNode(objectStamp, merge, new ValueNode[]{createdReceiver, exceptionValue}));
    }

    protected AbstractNewObjectNode createNewInstance(JNIGraphKit kit, ResolvedJavaType type, boolean fillContents) {
        return kit.append(new NewInstanceNode(type, fillContents));
    }

    /**
     * Builds a JNI {@code Call<Type>Method} call, returning a node that contains the return value
     * or null/zero/false when an exception occurred (in which case the exception becomes a JNI
     * pending exception).
     */
    public ValueNode createMethodCall(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, InvokeKind invokeKind, FrameStateBuilder state, ValueNode... args) {
        int bci = kit.bci();
        InvokeWithExceptionNode invoke = startInvokeWithRetainedException(kit, invokeMethod, invokeKind, state, bci, args);
        AbstractMergeNode invokeMerge = kit.endInvokeWithException();

        if (invoke.getStackKind() == JavaKind.Void && !invokeMethod.isConstructor()) {
            invokeMerge.setStateAfter(state.create(bci, invokeMerge));
            return null;
        }

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

    protected InvokeWithExceptionNode startInvokeWithRetainedException(JNIGraphKit kit, ResolvedJavaMethod invokeMethod,
                    InvokeKind kind, FrameStateBuilder state, int bci, ValueNode... args) {

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
