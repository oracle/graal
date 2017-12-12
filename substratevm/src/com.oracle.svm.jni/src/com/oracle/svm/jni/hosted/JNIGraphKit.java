/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.hosted;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.jni.JNIGeneratedMethodSupport;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * {@link HostedGraphKit} implementation with extensions that are specific to generated JNI code.
 */
class JNIGraphKit extends HostedGraphKit {

    JNIGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method) {
        super(debug, providers, method);
    }

    public ValueNode castObject(ValueNode value, ResolvedJavaType type) {
        ValueNode casted = value;
        if (!type.isJavaLangObject()) { // safe cast to expected type
            TypeReference typeRef = TypeReference.createTrusted(getAssumptions(), type);
            LogicNode condition = append(InstanceOfNode.createAllowNull(typeRef, value, null, null));
            if (!condition.isTautology()) {
                ObjectStamp stamp = StampFactory.object(typeRef, false);
                FixedGuardNode fixedGuard = append(new FixedGuardNode(condition, DeoptimizationReason.ClassCastException, DeoptimizationAction.None, false));
                casted = append(PiNode.create(value, stamp, fixedGuard));
            }
        }
        return casted;
    }

    private InvokeNode createStaticInvoke(String name, ValueNode... args) {
        return createInvoke(JNIGeneratedMethodSupport.class, name, InvokeKind.Static, getFrameState(), bci(), args);
    }

    private InvokeWithExceptionNode createStaticInvokeRetainException(String name, ValueNode... args) {
        ResolvedJavaMethod method = findMethod(JNIGeneratedMethodSupport.class, name, true);
        int invokeBci = bci();
        int exceptionEdgeBci = bci();
        InvokeWithExceptionNode invoke = startInvokeWithException(method, InvokeKind.Static, getFrameState(), invokeBci, exceptionEdgeBci, args);
        exceptionPart();
        ExceptionObjectNode exception = exceptionObject();
        retainPendingException(exception);
        endInvokeWithException();
        return invoke;
    }

    public InvokeWithExceptionNode nativeCallAddress(ValueNode linkage) {
        ResolvedJavaMethod method = findMethod(JNIGeneratedMethodSupport.class, "nativeCallAddress", true);
        int invokeBci = bci();
        int exceptionEdgeBci = bci();
        InvokeWithExceptionNode invoke = startInvokeWithException(method, InvokeKind.Static, getFrameState(), invokeBci, exceptionEdgeBci, linkage);
        exceptionPart();
        ExceptionObjectNode exception = exceptionObject();
        append(new UnwindNode(exception));
        endInvokeWithException();
        return invoke;
    }

    public InvokeNode nativeCallPrologue() {
        return createStaticInvoke("nativeCallPrologue");
    }

    public InvokeNode nativeCallEpilogue(ValueNode handleFrame) {
        return createStaticInvoke("nativeCallEpilogue", handleFrame);
    }

    public InvokeNode environment() {
        return createStaticInvoke("environment");
    }

    public InvokeNode boxObjectInLocalHandle(ValueNode obj) {
        return createStaticInvoke("boxObjectInLocalHandle", obj);
    }

    public InvokeNode unboxHandle(ValueNode handle) {
        return createStaticInvoke("unboxHandle", handle);
    }

    public InvokeNode getStaticPrimitiveFieldsArray() {
        return createStaticInvoke("getStaticPrimitiveFieldsArray");
    }

    public InvokeNode getStaticObjectFieldsArray() {
        return createStaticInvoke("getStaticObjectFieldsArray");
    }

    public InvokeNode retainPendingException(ValueNode obj) {
        return createStaticInvoke("retainPendingException", obj);
    }

    public InvokeWithExceptionNode rethrowPendingException() {
        ResolvedJavaMethod method = findMethod(JNIGeneratedMethodSupport.class, "rethrowPendingException", true);
        int invokeBci = bci();
        int exceptionEdgeBci = bci();
        InvokeWithExceptionNode invoke = startInvokeWithException(method, InvokeKind.Static, getFrameState(), invokeBci, exceptionEdgeBci);
        exceptionPart();
        ExceptionObjectNode exception = exceptionObject();
        append(new UnwindNode(exception));
        endInvokeWithException();
        return invoke;
    }

    public InvokeNode pinArrayAndGetAddress(ValueNode array, ValueNode isCopy) {
        return createStaticInvoke("pinArrayAndGetAddress", array, isCopy);
    }

    public InvokeNode unpinArrayByAddress(ValueNode address) {
        return createStaticInvoke("unpinArrayByAddress", address);
    }

    public InvokeWithExceptionNode getPrimitiveArrayRegionRetainException(JavaKind elementKind, ValueNode array, ValueNode start, ValueNode count, ValueNode buffer) {
        assert elementKind.isPrimitive();
        return createStaticInvokeRetainException("getPrimitiveArrayRegion", createObject(elementKind), array, start, count, buffer);
    }

    public InvokeWithExceptionNode setPrimitiveArrayRegionRetainException(JavaKind elementKind, ValueNode array, ValueNode start, ValueNode count, ValueNode buffer) {
        assert elementKind.isPrimitive();
        return createStaticInvokeRetainException("setPrimitiveArrayRegion", createObject(elementKind), array, start, count, buffer);
    }
}
