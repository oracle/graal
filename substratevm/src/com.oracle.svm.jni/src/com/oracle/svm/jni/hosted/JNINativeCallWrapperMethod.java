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

// Checkstyle: allow reflection

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;

import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.jni.access.JNIAccessFeature;
import com.oracle.svm.jni.access.JNINativeLinkage;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Generated code for calling a specific native method from Java code. The wrapper takes care of
 * transitioning to native code and back to Java, and if required, for boxing object arguments in
 * handles and for unboxing an object return value.
 */
class JNINativeCallWrapperMethod extends CustomSubstitutionMethod {
    private final JNINativeLinkage linkage;

    JNINativeCallWrapperMethod(ResolvedJavaMethod method) {
        super(method);
        linkage = createLinkage(method);
    }

    private static JNINativeLinkage createLinkage(ResolvedJavaMethod method) {
        ResolvedJavaMethod unwrapped = method;
        while (unwrapped instanceof WrappedJavaMethod) {
            unwrapped = ((WrappedJavaMethod) unwrapped).getWrapped();
        }
        String className = unwrapped.getDeclaringClass().getName();
        String descriptor = unwrapped.getSignature().toMethodDescriptor();
        return JNIAccessFeature.singleton().makeLinkage(className, unwrapped.getName(), descriptor);
    }

    @Override
    public boolean isSynthetic() {
        return true;
    }

    @Override
    public int getModifiers() {
        final int synthetic = 0x1000;
        // A synchronized method requires some special handling. Instead, if the wrapped method is
        // declared synchronized, we add graph nodes to lock and unlock accordingly.
        return (getOriginal().getModifiers() | synthetic) & ~(Modifier.NATIVE | Modifier.SYNCHRONIZED);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);
        StructuredGraph graph = kit.getGraph();

        InvokeWithExceptionNode handleFrame = kit.nativeCallPrologue();

        ValueNode callAddress;
        if (linkage.isBuiltInFunction()) {
            callAddress = kit.unique(new CGlobalDataLoadAddressNode(linkage.getBuiltInAddress()));
        } else {
            callAddress = kit.nativeCallAddress(kit.createObject(linkage));
        }

        ValueNode environment = kit.environment();

        JavaType javaReturnType = method.getSignature().getReturnType(null);
        JavaType[] javaArgumentTypes = method.toParameterTypes();
        List<ValueNode> javaArguments = kit.loadArguments(javaArgumentTypes);

        List<ValueNode> jniArguments = new ArrayList<>(2 + javaArguments.size());
        List<JavaType> jniArgumentTypes = new ArrayList<>(jniArguments.size());
        JavaType environmentType = providers.getMetaAccess().lookupJavaType(JNIEnvironment.class);
        JavaType objectHandleType = providers.getMetaAccess().lookupJavaType(JNIObjectHandle.class);
        jniArguments.add(environment);
        jniArgumentTypes.add(environmentType);
        if (method.isStatic()) {
            JavaConstant clazz = providers.getConstantReflection().asJavaClass(method.getDeclaringClass());
            ConstantNode clazzNode = ConstantNode.forConstant(clazz, providers.getMetaAccess(), graph);
            ValueNode box = kit.boxObjectInLocalHandle(clazzNode);
            jniArguments.add(box);
            jniArgumentTypes.add(objectHandleType);
        }
        for (int i = 0; i < javaArguments.size(); i++) {
            ValueNode arg = javaArguments.get(i);
            JavaType argType = javaArgumentTypes[i];
            if (javaArgumentTypes[i].getJavaKind().isObject()) {
                ValueNode obj = javaArguments.get(i);
                arg = kit.boxObjectInLocalHandle(obj);
                argType = objectHandleType;
            }
            jniArguments.add(arg);
            jniArgumentTypes.add(argType);
        }
        assert jniArguments.size() == jniArgumentTypes.size();
        JavaType jniReturnType = javaReturnType;
        if (jniReturnType.getJavaKind().isObject()) {
            jniReturnType = objectHandleType;
        }

        if (getOriginal().isSynchronized()) {
            ValueNode monitorObject;
            if (method.isStatic()) {
                Constant hubConstant = providers.getConstantReflection().asObjectHub(method.getDeclaringClass());
                DynamicHub hub = (DynamicHub) SubstrateObjectConstant.asObject(hubConstant);
                monitorObject = ConstantNode.forConstant(SubstrateObjectConstant.forObject(hub), providers.getMetaAccess(), graph);
            } else {
                monitorObject = javaArguments.get(0);
            }
            MonitorIdNode monitorId = graph.add(new MonitorIdNode(kit.getFrameState().lockDepth(false)));
            MonitorEnterNode monitorEnter = kit.append(new MonitorEnterNode(monitorObject, monitorId));
            kit.getFrameState().pushLock(monitorEnter.object(), monitorEnter.getMonitorId());
            monitorEnter.setStateAfter(kit.getFrameState().create(kit.bci(), monitorEnter));
        }

        kit.getFrameState().clearLocals();

        Signature jniSignature = new SimpleSignature(jniArgumentTypes, jniReturnType);
        ValueNode returnValue = kit.createCFunctionCall(callAddress, jniArguments, jniSignature, StatusSupport.STATUS_IN_NATIVE, false);

        if (getOriginal().isSynchronized()) {
            MonitorIdNode monitorId = kit.getFrameState().peekMonitorId();
            ValueNode monitorObject = kit.getFrameState().popLock();
            MonitorExitNode monitorExit = kit.append(new MonitorExitNode(monitorObject, monitorId, null));
            monitorExit.setStateAfter(kit.getFrameState().create(kit.bci(), monitorExit));
        }

        if (javaReturnType.getJavaKind().isObject()) {
            returnValue = kit.unboxHandle(returnValue); // before destroying handles in epilogue
        }
        kit.nativeCallEpilogue(handleFrame);
        kit.rethrowPendingException();
        if (javaReturnType.getJavaKind().isObject()) {
            // Just before return to always run the epilogue and never suppress a pending exception
            returnValue = castObject(kit, returnValue, (ResolvedJavaType) javaReturnType);
        }
        kit.createReturn(returnValue, javaReturnType.getJavaKind());

        return kit.finalizeGraph();
    }

    private static ValueNode castObject(JNIGraphKit kit, ValueNode object, ResolvedJavaType type) {
        ValueNode casted = object;
        if (!type.isJavaLangObject()) { // safe cast to expected type
            TypeReference typeRef = TypeReference.createTrusted(kit.getAssumptions(), type);
            LogicNode condition = kit.append(InstanceOfNode.createAllowNull(typeRef, object, null, null));
            if (!condition.isTautology()) {
                ObjectStamp stamp = StampFactory.object(typeRef, false);
                FixedGuardNode fixedGuard = kit.append(new FixedGuardNode(condition, DeoptimizationReason.ClassCastException, DeoptimizationAction.None, false));
                casted = kit.append(PiNode.create(object, stamp, fixedGuard));
            }
        }
        return casted;
    }
}
