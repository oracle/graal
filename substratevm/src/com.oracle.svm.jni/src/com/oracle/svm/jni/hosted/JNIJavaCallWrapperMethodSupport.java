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

import org.graalvm.collections.Pair;
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

    public AbstractNewObjectNode instantiate(ResolvedJavaType type, boolean fillContents) {
        return new NewInstanceNode(type, fillContents);
    }

    public ValueNode createInvoke(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, InvokeKind kind, @SuppressWarnings("unused") boolean isNewObjectCall, FrameStateBuilder state, int bci,
                    ValueNode... args) {
        Pair<InvokeWithExceptionNode, ValueNode> invokeAndException = startInvokeWithException(kit, invokeMethod, kind, state, bci, args);
        InvokeWithExceptionNode invoke = invokeAndException.getLeft();
        ValueNode exceptionValue = invokeAndException.getRight();
        AbstractMergeNode merge = kit.endInvokeWithException();
        return getReturnValue(kit, invokeMethod, state, bci, invoke, exceptionValue, merge);
    }

    protected Pair<InvokeWithExceptionNode, ValueNode> startInvokeWithException(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, InvokeKind kind, FrameStateBuilder state, int bci,
                    ValueNode... args) {
        ValueNode formerPendingException = kit.getAndClearPendingException();
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(invokeMethod, kind, state, bci, args);

        kit.noExceptionPart(); // no new exception was thrown, restore the formerly pending one
        kit.setPendingException(formerPendingException);

        kit.exceptionPart();
        ExceptionObjectNode exceptionObject = kit.exceptionObject();
        kit.setPendingException(exceptionObject);
        ValueNode exceptionValue = null;
        if (invoke.getStackKind() != JavaKind.Void) {
            exceptionValue = kit.unique(ConstantNode.defaultForKind(invoke.getStackKind()));
        }
        return Pair.create(invoke, exceptionValue);
    }

    protected ValueNode getReturnValue(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, FrameStateBuilder state, int bci, InvokeWithExceptionNode invoke, ValueNode exceptionValue,
                    AbstractMergeNode merge) {
        ValueNode returnValue = null;
        JavaKind returnKind = invokeMethod.getSignature().getReturnKind();
        if (invoke.getStackKind() != JavaKind.Void) {
            ValueNode[] inputs = {invoke, exceptionValue};
            returnValue = kit.getGraph().addWithoutUnique(new ValuePhiNode(invoke.stamp(NodeView.DEFAULT), merge, inputs));
            state.push(returnKind, returnValue);
        }
        merge.setStateAfter(state.create(bci, merge));
        if (invoke.getStackKind() != JavaKind.Void) {
            state.pop(returnKind);
        }
        return returnValue;
    }
}
