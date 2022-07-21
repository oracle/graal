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

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.jni.JNIGeneratedMethodSupport;
import com.oracle.svm.core.jni.access.JNIAccessibleMethod;
import com.oracle.svm.core.jni.access.JNIReflectionDictionary;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.hosted.phases.HostedGraphKit;

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
        return createStaticInvoke("nativeCallAddress", linkage);
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

    public InvokeWithExceptionNode getNewObjectAddress(ValueNode methodId) {
        return invokeJNIMethodObjectMethod("getNewObjectAddress", methodId);
    }

    /** We trust our stored class object to be non-null. */
    public ValueNode getDeclaringClassForMethod(ValueNode methodId) {
        InvokeWithExceptionNode declaringClass = invokeJNIMethodObjectMethod("getDeclaringClassObject", methodId);
        return createPiNode(declaringClass, ObjectStamp.pointerNonNull(declaringClass.stamp(NodeView.DEFAULT)));
    }

    public InvokeWithExceptionNode getJavaCallAddress(ValueNode methodId, ValueNode instance, ValueNode nonVirtual) {
        return createInvokeWithExceptionAndUnwind(findMethod(JNIAccessibleMethod.class, "getJavaCallAddress", Object.class, boolean.class),
                        InvokeKind.Special, getFrameState(), bci(), getUncheckedMethodObject(methodId), instance, nonVirtual);
    }

    public InvokeWithExceptionNode getJavaCallWrapperAddressFromMethodId(ValueNode methodId) {
        return invokeJNIMethodObjectMethod("getCallWrapperAddress", methodId);
    }

    public InvokeWithExceptionNode isStaticMethod(ValueNode methodId) {
        return invokeJNIMethodObjectMethod("isStatic", methodId);
    }

    /**
     * Used in native-to-Java call wrappers where the method ID has already been used to dispatch,
     * and we would have crashed if something is wrong, so we can avoid null and type checks.
     */
    private PiNode getUncheckedMethodObject(ValueNode methodId) {
        InvokeWithExceptionNode methodObj = createInvokeWithExceptionAndUnwind(
                        findMethod(JNIReflectionDictionary.class, "getObjectFromMethodID", JNIMethodId.class), InvokeKind.Static, getFrameState(), bci(), methodId);
        ObjectStamp stamp = StampFactory.objectNonNull(TypeReference.createExactTrusted(getMetaAccess().lookupJavaType(JNIAccessibleMethod.class)));
        return createPiNode(methodObj, stamp);
    }

    private InvokeWithExceptionNode invokeJNIMethodObjectMethod(String name, ValueNode methodId) {
        return createInvokeWithExceptionAndUnwind(findMethod(JNIAccessibleMethod.class, name), InvokeKind.Special, getFrameState(), bci(), getUncheckedMethodObject(methodId));
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
        return createStaticInvoke("rethrowPendingException");
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

    public ConstantNode createWord(long value) {
        return ConstantNode.forIntegerKind(wordTypes.getWordKind(), value, graph);
    }
}
