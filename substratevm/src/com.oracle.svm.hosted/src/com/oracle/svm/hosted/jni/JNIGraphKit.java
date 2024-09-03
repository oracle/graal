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
package com.oracle.svm.hosted.jni;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveEpilogue;
import com.oracle.svm.core.jni.JNIGeneratedMethodSupport;
import com.oracle.svm.core.jni.access.JNIAccessibleMethod;
import com.oracle.svm.core.jni.access.JNIReflectionDictionary;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIEnvEnterFatalOnFailurePrologue;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.util.Digest;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * {@link HostedGraphKit} implementation with extensions that are specific to generated JNI code.
 */
public class JNIGraphKit extends HostedGraphKit {

    JNIGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method) {
        super(debug, providers, method);
    }

    public static String signatureToIdentifier(ResolvedSignature<?> signature) {
        StringBuilder sb = new StringBuilder();
        boolean digest = false;
        for (var type : signature.toParameterList(null)) {
            if (type.getJavaKind().isPrimitive() || type.isJavaLangObject()) {
                sb.append(type.getJavaKind().getTypeChar());
            } else {
                sb.append(type.toClassName());
                digest = true;
            }
        }
        sb.append('_').append(signature.getReturnType().getJavaKind().getTypeChar());
        return digest ? Digest.digest(sb.toString()) : sb.toString();
    }

    public ValueNode checkObjectType(ValueNode uncheckedValue, ResolvedJavaType type, boolean checkNonNull) {
        ValueNode value = uncheckedValue;
        if (checkNonNull) {
            value = maybeCreateExplicitNullCheck(value);
        }
        if (type.isJavaLangObject()) {
            return value;
        }
        TypeReference typeRef = TypeReference.createTrusted(getAssumptions(), type);
        LogicNode isInstance = InstanceOfNode.createAllowNull(typeRef, value, null, null);
        if (!isInstance.isTautology()) {
            append(isInstance);
            ConstantNode expectedType = createConstant(getConstantReflection().asJavaClass(type), JavaKind.Object);
            GuardingNode guard = createCheckThrowingBytecodeException(isInstance, false, BytecodeExceptionNode.BytecodeExceptionKind.CLASS_CAST, value, expectedType);
            Stamp checkedStamp = value.stamp(NodeView.DEFAULT).improveWith(StampFactory.object(typeRef));
            value = unique(new PiNode(value, checkedStamp, guard.asNode()));
        }
        return value;
    }

    /** Masks bits to ensure that unused bytes in the stack representation are cleared. */
    public ValueNode maskNumericIntBytes(ValueNode value, JavaKind kind) {
        assert kind.isNumericInteger();
        int bits = kind.getByteCount() * Byte.SIZE;
        ValueNode narrowed = append(NarrowNode.create(value, bits, NodeView.DEFAULT));
        ValueNode widened = widenNumericInt(narrowed, kind);
        if (kind == JavaKind.Boolean) {
            LogicNode isZero = IntegerEqualsNode.create(widened, ConstantNode.forIntegerKind(kind.getStackKind(), 0), NodeView.DEFAULT);
            widened = append(ConditionalNode.create(isZero, ConstantNode.forBoolean(false), ConstantNode.forBoolean(true), NodeView.DEFAULT));
        }
        return widened;
    }

    public ValueNode widenNumericInt(ValueNode value, JavaKind kind) {
        assert kind.isNumericInteger();
        int stackBits = kind.getStackKind().getBitCount();
        if (kind.isUnsigned()) {
            return append(ZeroExtendNode.create(value, stackBits, NodeView.DEFAULT));
        } else {
            return append(SignExtendNode.create(value, stackBits, NodeView.DEFAULT));
        }
    }

    private InvokeWithExceptionNode createStaticInvoke(String name, ValueNode... args) {
        return createInvokeWithExceptionAndUnwind(findMethod(JNIGeneratedMethodSupport.class, name, true), InvokeKind.Static, getFrameState(), bci(), args);
    }

    public InvokeWithExceptionNode invokeNativeCallAddress(ValueNode linkage) {
        return createStaticInvoke("nativeCallAddress", linkage);
    }

    public InvokeWithExceptionNode invokeNativeCallPrologue() {
        return createStaticInvoke("nativeCallPrologue");
    }

    public void invokeNativeCallEpilogue(ValueNode handleFrame) {
        createStaticInvoke("nativeCallEpilogue", handleFrame);
    }

    public InvokeWithExceptionNode invokeEnvironment() {
        return createStaticInvoke("environment");
    }

    public InvokeWithExceptionNode invokeBoxObjectInLocalHandle(ValueNode obj) {
        return createStaticInvoke("boxObjectInLocalHandle", obj);
    }

    public InvokeWithExceptionNode invokeUnboxHandle(ValueNode handle) {
        return createStaticInvoke("unboxHandle", handle);
    }

    public InvokeWithExceptionNode invokeGetNewObjectAddress(ValueNode methodId) {
        return invokeJNIMethodObjectMethod("getNewObjectAddress", methodId);
    }

    /** We trust our stored class object to be non-null. */
    public ValueNode invokeGetDeclaringClassForMethod(ValueNode methodId) {
        InvokeWithExceptionNode declaringClass = invokeJNIMethodObjectMethod("getDeclaringClassObject", methodId);
        return createPiNode(declaringClass, ObjectStamp.pointerNonNull(declaringClass.stamp(NodeView.DEFAULT)));
    }

    public InvokeWithExceptionNode invokeGetJavaCallAddress(ValueNode methodId, ValueNode instance, ValueNode nonVirtual) {
        return createInvokeWithExceptionAndUnwind(findMethod(JNIAccessibleMethod.class, "getJavaCallAddress", Object.class, boolean.class),
                        InvokeKind.Special, getFrameState(), bci(), invokeGetUncheckedMethodObject(methodId), instance, nonVirtual);
    }

    public InvokeWithExceptionNode invokeGetJavaCallWrapperAddressFromMethodId(ValueNode methodId) {
        return invokeJNIMethodObjectMethod("getCallWrapperAddress", methodId);
    }

    public InvokeWithExceptionNode invokeIsStaticMethod(ValueNode methodId) {
        return invokeJNIMethodObjectMethod("isStatic", methodId);
    }

    /**
     * Used in native-to-Java call wrappers where the method ID has already been used to dispatch,
     * and we would have crashed if something is wrong, so we can avoid null and type checks.
     */
    private InvokeWithExceptionNode invokeGetUncheckedMethodObject(ValueNode methodId) {
        return createInvokeWithExceptionAndUnwind(findMethod(JNIReflectionDictionary.class, "getMethodByID", JNIMethodId.class),
                        InvokeKind.Static, getFrameState(), bci(), methodId);
    }

    private InvokeWithExceptionNode invokeJNIMethodObjectMethod(String name, ValueNode methodId) {
        return createInvokeWithExceptionAndUnwind(findMethod(JNIAccessibleMethod.class, name), InvokeKind.Special, getFrameState(), bci(), invokeGetUncheckedMethodObject(methodId));
    }

    public void invokeSetPendingException(ValueNode obj) {
        createStaticInvoke("setPendingException", obj);
    }

    public InvokeWithExceptionNode invokeGetAndClearPendingException() {
        return createStaticInvoke("getAndClearPendingException");
    }

    public void invokeRethrowPendingException() {
        createStaticInvoke("rethrowPendingException");
    }

    public void invokeJNIEnterIsolate(ValueNode env) {
        createInvokeWithExceptionAndUnwind(findMethod(JNIEnvEnterFatalOnFailurePrologue.class, "enter", true), InvokeKind.Static, getFrameState(), bci(), env);
    }

    public void invokeJNILeaveIsolate() {
        createInvokeWithExceptionAndUnwind(findMethod(LeaveEpilogue.class, "leave", true), InvokeKind.Static, getFrameState(), bci());
    }

    public ConstantNode createWord(long value) {
        return ConstantNode.forIntegerKind(getWordTypes().getWordKind(), value, graph);
    }
}
