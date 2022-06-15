/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.heap.SVMImageHeapScanner;
import com.oracle.svm.jni.access.JNIAccessFeature;
import com.oracle.svm.jni.access.JNINativeLinkage;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Generated code for calling a specific native method from Java code by finding its entry point and
 * delegating to a fitting {@link JNINativeCallWrapperMethod}.
 */
class JNINativeCallMethod extends CustomSubstitutionMethod {
    private final Field linkageBuiltInAddressField = ReflectionUtil.lookupField(JNINativeLinkage.class, "builtInAddress");

    private final JNINativeLinkage linkage;
    private final JNINativeCallWrapperMethod wrapperMethod;

    JNINativeCallMethod(ResolvedJavaMethod method, JNINativeCallWrapperMethod wrapperMethod) {
        super(method);
        this.linkage = createLinkage(method);
        this.wrapperMethod = wrapperMethod;
    }

    private static JNINativeLinkage createLinkage(ResolvedJavaMethod method) {
        assert !(method instanceof WrappedJavaMethod);
        String className = method.getDeclaringClass().getName();
        String descriptor = method.getSignature().toMethodDescriptor();
        return JNIAccessFeature.singleton().makeLinkage(className, method.getName(), descriptor);
    }

    @Override
    public int getModifiers() {
        // A synchronized method requires some special handling. Instead, if the native method is
        // declared synchronized, we add graph nodes to lock and unlock accordingly.
        return getOriginal().getModifiers() & ~Modifier.SYNCHRONIZED;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);
        ValueNode callAddress;
        if (linkage.isBuiltInFunction()) {
            callAddress = kit.unique(new CGlobalDataLoadAddressNode(linkage.getBuiltInAddress()));
            SVMImageHeapScanner.instance().rescanField(linkage, linkageBuiltInAddressField);
        } else {
            callAddress = kit.nativeCallAddress(kit.createObject(linkage));
        }

        JavaType[] params = method.toParameterTypes();
        List<ValueNode> args = kit.loadArguments(params);

        int nargs = (method.isStatic() ? 2 : 1) + args.size(); // static must be passed class object
        ValueNode[] wrapperArgs = new ValueNode[nargs];
        wrapperArgs[0] = callAddress;
        int i = 1;
        if (method.isStatic()) {
            JavaConstant clazz = providers.getConstantReflection().asJavaClass(method.getDeclaringClass());
            wrapperArgs[i++] = ConstantNode.forConstant(clazz, providers.getMetaAccess(), kit.getGraph());
        }
        for (ValueNode arg : args) {
            wrapperArgs[i++] = arg;
        }

        if (getOriginal().isSynchronized()) {
            ValueNode monitorObject = wrapperArgs[1];
            MonitorIdNode monitorId = kit.add(new MonitorIdNode(kit.getFrameState().lockDepth(false)));
            MonitorEnterNode monitorEnter = kit.append(new MonitorEnterNode(monitorObject, monitorId));
            kit.getFrameState().pushLock(monitorEnter.object(), monitorEnter.getMonitorId());
            monitorEnter.setStateAfter(kit.getFrameState().create(kit.bci(), monitorEnter));
        }

        ResolvedJavaMethod universeWrapperMethod = wrapperMethod;
        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        if (metaAccess.getWrapped() instanceof UniverseMetaAccess) {
            universeWrapperMethod = ((UniverseMetaAccess) metaAccess.getWrapped()).getUniverse().lookup(universeWrapperMethod);
        }
        universeWrapperMethod = metaAccess.getUniverse().lookup(universeWrapperMethod);
        ValueNode returnValue = kit.startInvokeWithException(universeWrapperMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), wrapperArgs);
        kit.exceptionPart();
        maybeExitMonitor(kit, kit.getFrameState().copy());
        kit.append(new UnwindNode(kit.exceptionObject()));
        kit.endInvokeWithException();

        maybeExitMonitor(kit, kit.getFrameState());

        JavaType returnType = method.getSignature().getReturnType(null);
        JavaKind returnKind = returnType.getJavaKind();
        if (returnKind.isObject()) {
            // Just before return to always run the epilogue and never suppress a pending exception
            returnValue = kit.checkObjectType(returnValue, (ResolvedJavaType) returnType, false);
        } else if (returnKind != JavaKind.Void && JNINativeCallWrapperMethod.returnKindWidensToLong(returnKind)) {
            returnValue = kit.maskIntegerBits(returnValue, returnKind);
        }
        kit.createReturn(returnValue, returnKind);
        return kit.finalizeGraph();
    }

    private void maybeExitMonitor(JNIGraphKit kit, FrameStateBuilder frameState) {
        if (getOriginal().isSynchronized()) {
            MonitorIdNode monitorId = frameState.peekMonitorId();
            ValueNode monitorObject = frameState.popLock();
            MonitorExitNode monitorExit = kit.append(new MonitorExitNode(monitorObject, monitorId, null));
            monitorExit.setStateAfter(frameState.create(kit.bci(), monitorExit));
        }
    }
}
