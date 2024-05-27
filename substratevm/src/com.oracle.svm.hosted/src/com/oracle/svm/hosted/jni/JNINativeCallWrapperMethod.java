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
package com.oracle.svm.hosted.jni;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;

import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.jni.access.JNINativeLinkage;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.hosted.heap.SVMImageHeapScanner;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Generated code for calling a specific native method from Java code. The wrapper takes care of
 * transitioning to native code and back to Java, and if required, for boxing object arguments in
 * handles and for unboxing an object return value.
 */
class JNINativeCallWrapperMethod extends CustomSubstitutionMethod {
    /** Line number that indicates a native method to {@link StackTraceElement}. */
    private static final int NATIVE_LINE_NUMBER = -2;
    private static final LineNumberTable LINE_NUMBER_TABLE = new LineNumberTable(new int[]{1}, new int[]{NATIVE_LINE_NUMBER});

    private final JNINativeLinkage linkage;
    private final Field linkageBuiltInAddressField = ReflectionUtil.lookupField(JNINativeLinkage.class, "builtInAddress");

    JNINativeCallWrapperMethod(ResolvedJavaMethod method) {
        super(method);
        assert !(method instanceof WrappedJavaMethod);
        this.linkage = createLinkage(method);
    }

    private static JNINativeLinkage createLinkage(ResolvedJavaMethod method) {
        String className = method.getDeclaringClass().getName();
        String descriptor = method.getSignature().toMethodDescriptor();
        return JNIAccessFeature.singleton().makeLinkage(className, method.getName(), descriptor);
    }

    @Override
    public int getModifiers() {
        // A synchronized method requires some special handling. Instead, if the wrapped method is
        // declared synchronized, we add graph nodes to lock and unlock accordingly.
        return getOriginal().getModifiers() & ~Modifier.SYNCHRONIZED;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return LINE_NUMBER_TABLE;
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        StackTraceElement ste = super.asStackTraceElement(bci);
        ste = new StackTraceElement(ste.getClassLoaderName(), ste.getModuleName(), ste.getModuleVersion(), ste.getClassName(), ste.getMethodName(), ste.getFileName(), NATIVE_LINE_NUMBER);
        assert ste.isNativeMethod();
        return ste;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method, purpose);
        StructuredGraph graph = kit.getGraph();

        ValueNode callAddress;
        if (linkage.isBuiltInFunction()) {
            Function<String, CGlobalDataInfo> createSymbol = symbolName -> CGlobalDataFeature.singleton().registerAsAccessedOrGet(CGlobalDataFactory.forSymbol(symbolName));
            CGlobalDataInfo builtinAddress = linkage.getOrCreateBuiltInAddress(createSymbol);
            callAddress = kit.unique(new CGlobalDataLoadAddressNode(builtinAddress));
            SVMImageHeapScanner.instance().rescanField(linkage, linkageBuiltInAddressField);
        } else {
            callAddress = kit.invokeNativeCallAddress(kit.createObject(linkage));
        }

        ValueNode environment = kit.invokeEnvironment();

        /* After the JNI prologue, we must not invoke methods that may throw an exception. */
        InvokeWithExceptionNode handleFrame = kit.invokeNativeCallPrologue();

        JavaType javaReturnType = method.getSignature().getReturnType(null);
        JavaType[] javaArgumentTypes = method.toParameterTypes();
        List<ValueNode> javaArguments = kit.loadArguments(javaArgumentTypes);

        List<ValueNode> jniArguments = new ArrayList<>(2 + javaArguments.size());
        List<JavaType> jniArgumentTypes = new ArrayList<>(2 + javaArguments.size());
        JavaType environmentType = providers.getMetaAccess().lookupJavaType(JNIEnvironment.class);
        JavaType objectHandleType = providers.getMetaAccess().lookupJavaType(JNIObjectHandle.class);
        jniArguments.add(environment);
        jniArgumentTypes.add(environmentType);
        if (method.isStatic()) {
            JavaConstant clazz = providers.getConstantReflection().asJavaClass(method.getDeclaringClass());
            ConstantNode clazzNode = ConstantNode.forConstant(clazz, providers.getMetaAccess(), graph);
            /* Thrown exceptions may cause a memory leak, see GR-54276. */
            ValueNode box = kit.invokeBoxObjectInLocalHandle(clazzNode);
            jniArguments.add(box);
            jniArgumentTypes.add(objectHandleType);
        }
        for (int i = 0; i < javaArguments.size(); i++) {
            ValueNode arg = javaArguments.get(i);
            JavaType argType = javaArgumentTypes[i];
            if (javaArgumentTypes[i].getJavaKind().isObject()) {
                ValueNode obj = javaArguments.get(i);
                /* Thrown exceptions may cause a memory leak, see GR-54276. */
                arg = kit.invokeBoxObjectInLocalHandle(obj);
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

        /* Thrown exceptions may cause a memory leak, see GR-54276. */
        if (getOriginal().isSynchronized()) {
            ValueNode monitorObject;
            if (method.isStatic()) {
                JavaConstant hubConstant = (JavaConstant) providers.getConstantReflection().asObjectHub(method.getDeclaringClass());
                monitorObject = ConstantNode.forConstant(hubConstant, providers.getMetaAccess(), graph);
            } else {
                monitorObject = kit.maybeCreateExplicitNullCheck(javaArguments.get(0));
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
            /*
             * Must be invoked before the handles are destroyed in the epilogue. Thrown exceptions may cause
             * a memory leak, see GR-54276.
             */
            returnValue = kit.invokeUnboxHandle(returnValue);
        }
        kit.invokeNativeCallEpilogue(handleFrame);
        kit.invokeRethrowPendingException();
        if (javaReturnType.getJavaKind().isObject()) {
            // Just before return to always run the epilogue and never suppress a pending exception
            returnValue = kit.checkObjectType(returnValue, (ResolvedJavaType) javaReturnType, false);
        }
        kit.createReturn(returnValue, javaReturnType.getJavaKind());

        return kit.finalizeGraph();
    }
}
