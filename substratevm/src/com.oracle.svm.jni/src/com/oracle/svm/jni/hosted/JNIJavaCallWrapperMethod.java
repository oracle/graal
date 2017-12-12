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

// Checkstyle: allow reflection

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode.EnterAction;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.graal.nodes.CInterfaceReadNode;
import com.oracle.svm.core.graal.nodes.VaListNextArgNode;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;
import com.oracle.svm.jni.JNIJavaCallWrappers;
import com.oracle.svm.jni.access.JNINativeLinkage;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIValue;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Generated code for calling a specific Java method from native code. The wrapper takes care of
 * transitioning to a Java context and back to native code, for catching and retaining unhandled
 * exceptions, and if required, for unboxing object handle arguments and boxing an object return
 * value.
 */
public final class JNIJavaCallWrapperMethod extends JNIGeneratedMethod {

    public enum CallVariant {
        VARARGS,
        ARRAY,
        VA_LIST,
    }

    private final NativeLibraries nativeLibs;

    private final ResolvedJavaType declaringClass;
    private final ConstantPool constantPool;
    private final Executable reflectMethod;
    private final CallVariant callVariant;
    private final ResolvedJavaMethod targetMethod;
    private final Signature signature;

    public JNIJavaCallWrapperMethod(Executable reflectMethod, CallVariant callVariant, MetaAccessProvider metaAccess, NativeLibraries nativeLibs) {
        this.declaringClass = metaAccess.lookupJavaType(JNIJavaCallWrappers.class);
        this.constantPool = JNIJavaCallWrappers.getConstantPool(metaAccess);
        this.reflectMethod = reflectMethod;
        this.nativeLibs = nativeLibs;
        this.callVariant = callVariant;
        this.targetMethod = metaAccess.lookupJavaMethod(reflectMethod);
        this.signature = createSignature(metaAccess);
    }

    private JNISignature createSignature(MetaAccessProvider metaAccess) {
        ResolvedJavaType objectHandle = metaAccess.lookupJavaType(JNIObjectHandle.class);
        List<JavaType> args = new ArrayList<>();
        args.add(metaAccess.lookupJavaType(JNIEnvironment.class));
        args.add(objectHandle); // this (instance method) or class (static method)
        args.add(metaAccess.lookupJavaType(JNIMethodId.class));
        Signature targetSignature = targetMethod.getSignature();
        if (callVariant == CallVariant.VARARGS) {
            for (JavaType targetArg : targetSignature.toParameterTypes(null)) {
                JavaKind kind = targetArg.getJavaKind();
                if (kind.isObject()) {
                    args.add(objectHandle);
                } else if (kind == JavaKind.Float) { // C varargs promote float to double
                    args.add(metaAccess.lookupJavaType(JavaKind.Double.toJavaClass()));
                } else {
                    args.add(targetArg);
                }
            }
        } else if (callVariant == CallVariant.ARRAY) {
            args.add(metaAccess.lookupJavaType(JNIValue.class)); // const jvalue *
        } else if (callVariant == CallVariant.VA_LIST) {
            args.add(metaAccess.lookupJavaType(WordBase.class)); // va_list (a pointer of some kind)
        } else {
            throw VMError.shouldNotReachHere();
        }
        JavaType returnType = targetSignature.getReturnType(null);
        if (returnType.getJavaKind().isObject()) {
            returnType = objectHandle;
        }
        return new JNISignature(args, returnType);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);
        StructuredGraph graph = kit.getGraph();
        FrameStateBuilder state = new FrameStateBuilder(null, method, graph);
        state.initializeForMethodStart(null, true, providers.getGraphBuilderPlugins());

        JavaKind vmThreadKind = metaAccess.lookupJavaType(JNIEnvironment.class).getJavaKind();
        ValueNode vmThread = kit.loadLocal(0, vmThreadKind);
        kit.append(new CEntryPointEnterNode(EnterAction.Enter, vmThread));

        ResolvedJavaMethod invokeMethod = providers.getMetaAccess().lookupJavaMethod(reflectMethod);
        Signature invokeSignature = invokeMethod.getSignature();
        ValueNode[] args = loadAndUnboxArguments(kit, providers, invokeMethod, invokeSignature);
        InvokeKind kind = invokeMethod.isStatic() ? InvokeKind.Static : InvokeKind.Virtual;
        ValueNode returnValue = createInvoke(kit, invokeMethod, kind, state, kit.bci(), args);
        JavaKind returnKind = getSignature().getReturnKind();
        if (invokeSignature.getReturnKind().isObject()) {
            returnValue = kit.boxObjectInLocalHandle(returnValue);
        }
        kit.append(new CEntryPointLeaveNode(LeaveAction.Leave));
        kit.createReturn(returnValue, returnKind);

        assert graph.verify();
        return graph;
    }

    private static ValueNode createInvoke(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, InvokeKind kind, FrameStateBuilder state, int bci, ValueNode... args) {
        int exceptionEdgeBci = kit.bci();
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(invokeMethod, kind, state, bci, exceptionEdgeBci, args);

        kit.exceptionPart();
        ExceptionObjectNode exceptionObject = kit.exceptionObject();
        kit.retainPendingException(exceptionObject);
        ValueNode exceptionValue = null;
        if (invoke.getStackKind() != JavaKind.Void) {
            exceptionValue = kit.unique(ConstantNode.defaultForKind(invoke.getStackKind()));
        }

        AbstractMergeNode merge = kit.endInvokeWithException();
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

    private ValueNode[] loadAndUnboxArguments(JNIGraphKit kit, HostedProviders providers, ResolvedJavaMethod invokeMethod, Signature invokeSignature) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        List<ValueNode> args = new ArrayList<>();
        int javaIndex = 0;
        javaIndex += metaAccess.lookupJavaType(JNIEnvironment.class).getJavaKind().getSlotCount();
        if (!invokeMethod.isStatic()) {
            JavaKind kind = metaAccess.lookupJavaType(JNIObjectHandle.class).getJavaKind();
            ValueNode receiverHandle = kit.loadLocal(javaIndex, kind);
            ValueNode receiver = kit.unboxHandle(receiverHandle);
            receiver = kit.castObject(receiver, invokeMethod.getDeclaringClass());
            args.add(receiver);
        }
        javaIndex += metaAccess.lookupJavaType(JNIObjectHandle.class).getJavaKind().getSlotCount();
        javaIndex += metaAccess.lookupJavaType(JNIMethodId.class).getJavaKind().getSlotCount();
        int count = invokeSignature.getParameterCount(false);
        if (callVariant == CallVariant.VARARGS) {
            for (int i = 0; i < count; i++) {
                ResolvedJavaType type = (ResolvedJavaType) invokeSignature.getParameterType(i, null);
                JavaKind kind = type.getJavaKind();
                JavaKind loadKind = kind;
                if (loadKind == JavaKind.Float) { // C varargs promote float to double
                    loadKind = JavaKind.Double;
                }
                ValueNode value = kit.loadLocal(javaIndex, loadKind);
                if (kind == JavaKind.Float) {
                    value = kit.unique(new FloatConvertNode(FloatConvert.D2F, value));
                } else if (kind.isObject()) {
                    value = kit.unboxHandle(value);
                    value = kit.castObject(value, type);
                }
                args.add(value);
                javaIndex += loadKind.getSlotCount();
            }
        } else if (callVariant == CallVariant.ARRAY) {
            ResolvedJavaType elementType = metaAccess.lookupJavaType(JNIValue.class);
            int elementSize = SizeOf.get(JNIValue.class);
            ValueNode array = kit.loadLocal(javaIndex, elementType.getJavaKind());
            for (int i = 0; i < count; i++) {
                ResolvedJavaType type = (ResolvedJavaType) invokeSignature.getParameterType(i, null);
                JavaKind readKind = type.getJavaKind();
                StructFieldInfo fieldInfo = getJNIValueOffsetOf(elementType, readKind);
                int offset = i * elementSize + fieldInfo.getOffsetInfo().getProperty();
                ConstantNode offsetConstant = kit.createConstant(JavaConstant.forInt(offset), providers.getWordTypes().getWordKind());
                OffsetAddressNode address = kit.unique(new OffsetAddressNode(array, offsetConstant));
                LocationIdentity locationIdentity = fieldInfo.getLocationIdentity();
                if (locationIdentity == null) {
                    locationIdentity = LocationIdentity.any();
                }
                Stamp readStamp = getNarrowStamp(providers, readKind);
                ValueNode value = kit.append(new CInterfaceReadNode(address, locationIdentity, readStamp, BarrierType.NONE, "args[" + i + "]"));
                JavaKind stackKind = readKind.getStackKind();
                if (readKind != stackKind) {
                    assert stackKind.getBitCount() > readKind.getBitCount() : "read kind must be narrower than stack kind";
                    if (readKind.isUnsigned()) { // needed or another op may illegally sign-extend
                        value = kit.unique(new ZeroExtendNode(value, stackKind.getBitCount()));
                    } else {
                        value = kit.unique(new SignExtendNode(value, stackKind.getBitCount()));
                    }
                } else if (readKind.isObject()) {
                    value = kit.unboxHandle(value);
                    value = kit.castObject(value, type);
                }
                args.add(value);
            }
        } else if (callVariant == CallVariant.VA_LIST) {
            ValueNode valist = kit.loadLocal(javaIndex, metaAccess.lookupJavaType(WordBase.class).getJavaKind());
            for (int i = 0; i < count; i++) {
                ResolvedJavaType type = (ResolvedJavaType) invokeSignature.getParameterType(i, null);
                JavaKind loadKind = type.getJavaKind();
                if (loadKind.isObject()) {
                    loadKind = providers.getWordTypes().getWordKind();
                }
                ValueNode value = kit.append(new VaListNextArgNode(loadKind, valist));
                if (type.getJavaKind().isObject()) {
                    value = kit.unboxHandle(value);
                    value = kit.castObject(value, type);
                }
                args.add(value);
            }
        } else {
            throw VMError.unsupportedFeature("Call variant: " + callVariant);
        }
        return args.toArray(new ValueNode[0]);
    }

    private static Stamp getNarrowStamp(HostedProviders providers, JavaKind kind) {
        if (kind.isNumericInteger()) {
            // avoid widened stamp to prevent reading undefined bits
            if (kind.isUnsigned()) {
                return StampFactory.forUnsignedInteger(kind.getBitCount(), kind.getMinValue(), kind.getMaxValue());
            } else {
                return StampFactory.forInteger(kind.getBitCount(), kind.getMinValue(), kind.getMaxValue());
            }
        } else if (kind.isObject()) {
            ResolvedJavaType objectHandle = providers.getMetaAccess().lookupJavaType(JNIObjectHandle.class);
            return providers.getWordTypes().getWordStamp(objectHandle);
        } else {
            return StampFactory.forKind(kind);
        }
    }

    private StructFieldInfo getJNIValueOffsetOf(ResolvedJavaType jniValueType, JavaKind kind) {
        String name = String.valueOf(kind.isObject() ? 'l' : Character.toLowerCase(kind.getTypeChar()));
        StructInfo structInfo = (StructInfo) nativeLibs.findElementInfo(jniValueType);
        for (ElementInfo elementInfo : structInfo.getChildren()) {
            if (elementInfo instanceof StructFieldInfo) {
                StructFieldInfo fieldInfo = (StructFieldInfo) elementInfo;
                if (name.equals(fieldInfo.getName())) {
                    return fieldInfo;
                }
            }
        }
        throw VMError.shouldNotReachHere();
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public String getName() {
        String full = targetMethod.getDeclaringClass().getName() + "." + targetMethod.getName() + targetMethod.getSignature().toMethodDescriptor();
        return "jniInvoke_" + callVariant.name() + ":" + JNINativeLinkage.mangle(full);
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
    }

}
