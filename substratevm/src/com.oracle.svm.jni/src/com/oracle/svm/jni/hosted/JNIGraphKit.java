/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.jni.JNIGeneratedMethodSupport;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * {@link HostedGraphKit} implementation with extensions that are specific to generated JNI code.
 */
class JNIGraphKit extends HostedGraphKit {

    JNIGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method) {
        super(debug, providers, method);
    }

    private InvokeWithExceptionNode createStaticInvoke(String name, ValueNode... args) {
        return createInvokeWithExceptionAndUnwind(findMethod(JNIGeneratedMethodSupport.class, name, true), InvokeKind.Static, getFrameState(), bci(), args);
    }

    private FixedWithNextNode createStaticInvokeRetainException(String name, ValueNode... args) {
        ResolvedJavaMethod method = findMethod(JNIGeneratedMethodSupport.class, name, true);
        int invokeBci = bci();
        startInvokeWithException(method, InvokeKind.Static, getFrameState(), invokeBci, args);
        exceptionPart();
        ExceptionObjectNode exception = exceptionObject();
        setPendingException(exception);
        endInvokeWithException();
        return lastFixedNode;
    }

    public InvokeWithExceptionNode nativeCallAddress(ValueNode linkage) {
        ResolvedJavaMethod method = findMethod(JNIGeneratedMethodSupport.class, "nativeCallAddress", true);
        int invokeBci = bci();
        return createInvokeWithExceptionAndUnwind(method, InvokeKind.Static, getFrameState(), invokeBci, linkage);
    }

    public InvokeWithExceptionNode nativeCallPrologue() {
        return createStaticInvoke("nativeCallPrologue");
    }

    public InvokeWithExceptionNode nativeCallEpilogue(ValueNode handleFrame) {
        return createStaticInvoke("nativeCallEpilogue", handleFrame);
    }

    public InvokeWithExceptionNode environment() {
        return createStaticInvoke("environment");
    }

    public InvokeWithExceptionNode boxObjectInLocalHandle(ValueNode obj) {
        return createStaticInvoke("boxObjectInLocalHandle", obj);
    }

    public InvokeWithExceptionNode unboxHandle(ValueNode handle) {
        return createStaticInvoke("unboxHandle", handle);
    }

    public InvokeWithExceptionNode getFieldOffsetFromId(ValueNode fieldId) {
        return createStaticInvoke("getFieldOffsetFromId", fieldId);
    }

    public InvokeWithExceptionNode getStaticPrimitiveFieldsArray() {
        return createStaticInvoke("getStaticPrimitiveFieldsArray");
    }

    public InvokeWithExceptionNode getStaticObjectFieldsArray() {
        return createStaticInvoke("getStaticObjectFieldsArray");
    }

    public InvokeWithExceptionNode setPendingException(ValueNode obj) {
        return createStaticInvoke("setPendingException", obj);
    }

    public InvokeWithExceptionNode getAndClearPendingException() {
        return createStaticInvoke("getAndClearPendingException");
    }

    public InvokeWithExceptionNode rethrowPendingException() {
        ResolvedJavaMethod method = findMethod(JNIGeneratedMethodSupport.class, "rethrowPendingException", true);
        int invokeBci = bci();
        return createInvokeWithExceptionAndUnwind(method, InvokeKind.Static, getFrameState(), invokeBci);
    }

    public InvokeWithExceptionNode pinArrayAndGetAddress(ValueNode array, ValueNode isCopy) {
        return createStaticInvoke("pinArrayAndGetAddress", array, isCopy);
    }

    public InvokeWithExceptionNode unpinArrayByAddress(ValueNode address) {
        return createStaticInvoke("unpinArrayByAddress", address);
    }

    public FixedWithNextNode getPrimitiveArrayRegionRetainException(JavaKind elementKind, ValueNode array, ValueNode start, ValueNode count, ValueNode buffer) {
        assert elementKind.isPrimitive();
        return createStaticInvokeRetainException("getPrimitiveArrayRegion", createObject(elementKind), array, start, count, buffer);
    }

    public FixedWithNextNode setPrimitiveArrayRegionRetainException(JavaKind elementKind, ValueNode array, ValueNode start, ValueNode count, ValueNode buffer) {
        assert elementKind.isPrimitive();
        return createStaticInvokeRetainException("setPrimitiveArrayRegion", createObject(elementKind), array, start, count, buffer);
    }
}
