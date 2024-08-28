/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.Platform;
import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.nodes.CInterfaceReadNode;
import com.oracle.svm.core.graal.nodes.ReadCallerStackPointerNode;
import com.oracle.svm.core.graal.nodes.VaListInitializationNode;
import com.oracle.svm.core.graal.nodes.VaListNextArgNode;
import com.oracle.svm.core.jni.CallVariant;
import com.oracle.svm.core.jni.JNIJavaCallVariantWrapperHolder;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.EntryPointCallStubMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.hosted.meta.HostedMetaAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * Generates uninterruptible code for taking arguments according to a specific signature and
 * {@link CallVariant} and passing them on to a {@link JNIJavaCallWrapperMethod} which does the
 * actual Java call. This method also enters the isolate and catches any exception.
 */
public class JNIJavaCallVariantWrapperMethod extends EntryPointCallStubMethod {

    private final Signature callWrapperSignature;
    private final CallVariant callVariant;
    private final boolean nonVirtual;

    public JNIJavaCallVariantWrapperMethod(SimpleSignature callWrapperSignature, CallVariant callVariant, boolean nonVirtual, MetaAccessProvider originalMetaAccess, WordTypes wordTypes) {
        super(createName(callWrapperSignature, callVariant, nonVirtual),
                        originalMetaAccess.lookupJavaType(JNIJavaCallVariantWrapperHolder.class),
                        createSignature(callWrapperSignature, callVariant, nonVirtual, originalMetaAccess, wordTypes),
                        JNIJavaCallVariantWrapperHolder.getConstantPool(originalMetaAccess));
        this.callWrapperSignature = callWrapperSignature;
        this.callVariant = callVariant;
        this.nonVirtual = nonVirtual;
    }

    private static String createName(SimpleSignature targetSignature, CallVariant callVariant, boolean nonVirtual) {
        return "invoke" + targetSignature.getIdentifier() + "_" + callVariant.name() + (nonVirtual ? "_Nonvirtual" : "");
    }

    private static Signature createSignature(Signature callWrapperSignature, CallVariant callVariant, boolean nonVirtual, MetaAccessProvider originalMetaAccess, WordTypes wordTypes) {
        JavaKind wordKind = wordTypes.getWordKind();
        List<JavaKind> args = new ArrayList<>();
        args.add(wordKind); // JNIEnv
        args.add(wordKind); // handle: this (instance method) or class (static method)
        if (nonVirtual) {
            args.add(wordKind); // handle: class of implementation to invoke
        }
        args.add(wordKind); // jmethodID
        if (callVariant == CallVariant.VARARGS) {
            int count = callWrapperSignature.getParameterCount(false);
            for (int i = 3; i < count; i++) { // skip receiver/class, jmethodID, non-virtual args
                JavaKind kind = callWrapperSignature.getParameterKind(i);
                if (kind.isObject()) {
                    args.add(wordKind); // handle
                } else if (Platform.includedIn(Platform.RISCV64.class) && (kind == JavaKind.Float || kind == JavaKind.Double)) {
                    args.add(JavaKind.Long);
                } else if (kind == JavaKind.Float) {
                    // C varargs promote float to double (C99, 6.5.2.2-6)
                    args.add(JavaKind.Double);
                } else { // C varargs promote sub-words to int (C99, 6.5.2.2-6)
                    args.add(kind.getStackKind());
                }
            }
        } else if (callVariant == CallVariant.ARRAY) {
            args.add(wordKind); // const jvalue *
        } else if (callVariant == CallVariant.VA_LIST) {
            args.add(wordKind); // va_list (a pointer of some kind)
        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(callVariant); // ExcludeFromJacocoGeneratedReport
        }
        JavaKind returnType = callWrapperSignature.getReturnKind();
        if (returnType.isObject()) {
            returnType = wordKind; // handle
        }
        return SimpleSignature.fromKinds(args.toArray(JavaKind[]::new), returnType, originalMetaAccess);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method, purpose);

        AnalysisMetaAccess aMetaAccess = (AnalysisMetaAccess) ((metaAccess instanceof AnalysisMetaAccess) ? metaAccess : metaAccess.getWrapped());
        Signature invokeSignature = aMetaAccess.getUniverse().lookup(callWrapperSignature, getDeclaringClass());
        if (metaAccess instanceof HostedMetaAccess) {
            // signature might not exist in the hosted universe because it does not match any method
            invokeSignature = new WrappedSignature(metaAccess.getUniverse(), invokeSignature, getDeclaringClass());
        }

        JavaKind wordKind = providers.getWordTypes().getWordKind();
        int slotIndex = 0;
        ValueNode env = kit.loadLocal(slotIndex, wordKind);
        slotIndex += wordKind.getSlotCount();
        ValueNode receiverOrClassHandle = kit.loadLocal(slotIndex, wordKind);
        slotIndex += wordKind.getSlotCount();
        if (nonVirtual) {
            slotIndex += wordKind.getSlotCount();
        }
        ValueNode methodId = kit.loadLocal(slotIndex, wordKind);
        slotIndex += wordKind.getSlotCount();

        kit.invokeJNIEnterIsolate(env);
        ValueNode callAddress = kit.invokeGetJavaCallWrapperAddressFromMethodId(methodId);

        List<ValueNode> args = new ArrayList<>();
        args.add(receiverOrClassHandle);
        args.add(methodId);
        args.add(kit.createInt(nonVirtual ? 1 : 0));
        args.addAll(loadArguments(kit, providers, invokeSignature, args.size(), slotIndex));

        ValueNode formerPendingException = kit.invokeGetAndClearPendingException();

        StampPair returnStamp = StampFactory.forDeclaredType(kit.getAssumptions(), invokeSignature.getReturnType(null), false);
        CallTargetNode callTarget = new IndirectCallTargetNode(callAddress, args.toArray(ValueNode[]::new), returnStamp, invokeSignature.toParameterTypes(null),
                        null, SubstrateCallingConventionKind.Java.toType(true), InvokeKind.Static);

        int invokeBci = kit.bci();
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(callTarget, kit.getFrameState(), invokeBci);
        kit.noExceptionPart();
        kit.invokeSetPendingException(formerPendingException);
        kit.exceptionPart();
        kit.invokeSetPendingException(kit.exceptionObject());
        AbstractMergeNode invokeMerge = kit.endInvokeWithException();

        ValueNode returnValue = null;
        JavaKind returnKind = JavaKind.Void;
        if (invoke.getStackKind() == JavaKind.Void) {
            invokeMerge.setStateAfter(kit.getFrameState().create(invokeBci, invokeMerge));
        } else {
            ValueNode exceptionValue = kit.unique(ConstantNode.defaultForKind(invoke.getStackKind()));
            ValueNode[] inputs = {invoke, exceptionValue};
            returnValue = kit.getGraph().addWithoutUnique(new ValuePhiNode(invoke.stamp(NodeView.DEFAULT), invokeMerge, inputs));
            returnKind = returnValue.getStackKind();
            kit.getFrameState().push(returnKind, returnValue);
            invokeMerge.setStateAfter(kit.getFrameState().create(invokeBci, invokeMerge));
            kit.getFrameState().pop(returnKind);
        }

        kit.invokeJNILeaveIsolate();
        kit.createReturn(returnValue, returnKind);
        return kit.finalizeGraph();
    }

    /**
     * Creates {@linkplain ValueNode IR nodes} for the arguments passed to the JNI call. The
     * arguments do not include the receiver of the call, but only the actual arguments passed to
     * the JNI target function.
     *
     * @return List of created argument nodes and their type
     */
    private List<ValueNode> loadArguments(JNIGraphKit kit, HostedProviders providers, Signature invokeSignature, int firstParamIndex, int firstSlotIndex) {
        JavaKind wordKind = providers.getWordTypes().getWordKind();
        List<ValueNode> args = new ArrayList<>();
        int slotIndex = firstSlotIndex;
        int count = invokeSignature.getParameterCount(false);
        // Windows and iOS CallVariant.VA_LIST is identical to CallVariant.ARRAY
        // iOS CallVariant.VARARGS stores values as an array on the stack
        if (callVariant == CallVariant.ARRAY ||
                        (Platform.includedIn(Platform.DARWIN_AARCH64.class) && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) ||
                        (Platform.includedIn(Platform.WINDOWS.class) && callVariant == CallVariant.VA_LIST)) {
            ValueNode array;
            if (callVariant == CallVariant.VARARGS) {
                array = kit.append(new ReadCallerStackPointerNode());
            } else {
                array = kit.loadLocal(slotIndex, wordKind);
            }
            for (int i = firstParamIndex; i < count; i++) {
                JavaKind kind = invokeSignature.getParameterKind(i);
                assert kind == kind.getStackKind() : "sub-int conversions and bit masking must happen in JNIJavaCallWrapperMethod";
                JavaKind readKind = kind;
                if (kind == JavaKind.Float && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) {
                    readKind = JavaKind.Double;
                } else if (kind.isObject()) {
                    readKind = wordKind;
                }
                /*
                 * jvalue is a union, and all members of a C union should have the same address,
                 * which is that of the union itself (C99 6.7.2.1-14). Therefore, we do not need a
                 * field offset lookup and we can also read a larger type than we need (e.g. int for
                 * char) and mask the extra bits on little-endian architectures so that we can reuse
                 * a wrapper for more different signatures.
                 */
                int offset = (i - firstParamIndex) * wordKind.getByteCount();
                ConstantNode offsetConstant = kit.createConstant(JavaConstant.forIntegerKind(wordKind, offset), wordKind);
                OffsetAddressNode address = kit.unique(new OffsetAddressNode(array, offsetConstant));
                Stamp readStamp = StampFactory.forKind(readKind);
                ValueNode value = kit.append(new CInterfaceReadNode(address, LocationIdentity.any(), readStamp, BarrierType.NONE, MemoryOrderMode.PLAIN, "args[" + i + "]"));
                if (kind == JavaKind.Float && readKind == JavaKind.Double) {
                    value = kit.unique(new FloatConvertNode(FloatConvert.D2F, value));
                }
                args.add(value);
            }
        } else if (callVariant == CallVariant.VARARGS) {
            for (int i = firstParamIndex; i < count; i++) {
                JavaKind kind = invokeSignature.getParameterKind(i);
                assert kind == kind.getStackKind() : "sub-int conversions and bit masking must happen in JNIJavaCallWrapperMethod";
                JavaKind loadKind = kind;
                if (Platform.includedIn(Platform.RISCV64.class) && (kind == JavaKind.Double || kind == JavaKind.Float)) {
                    loadKind = JavaKind.Long;
                } else if (loadKind == JavaKind.Float) {
                    loadKind = JavaKind.Double; // C varargs promote float to double (C99 6.5.2.2-6)
                }
                ValueNode value = kit.loadLocal(slotIndex, loadKind);
                if (Platform.includedIn(Platform.RISCV64.class) && (kind == JavaKind.Double || kind == JavaKind.Float)) {
                    value = kit.unique(new ReinterpretNode(JavaKind.Double, value));
                }
                if (kind == JavaKind.Float) {
                    value = kit.unique(new FloatConvertNode(FloatConvert.D2F, value));
                }
                args.add(value);
                slotIndex += loadKind.getSlotCount();
            }
        } else if (callVariant == CallVariant.VA_LIST) {
            ValueNode vaList = kit.loadLocal(slotIndex, wordKind);
            FixedWithNextNode vaListInitialized = kit.append(new VaListInitializationNode(vaList));
            for (int i = firstParamIndex; i < count; i++) {
                JavaKind kind = invokeSignature.getParameterKind(i);
                if (kind.isObject()) {
                    kind = wordKind;
                }
                assert kind == kind.getStackKind() : "sub-int conversions and bit masking must happen in JNIJavaCallWrapperMethod";
                ValueNode value = kit.append(new VaListNextArgNode(kind, vaListInitialized));
                args.add(value);
            }
        } else {
            throw VMError.unsupportedFeature("Call variant: " + callVariant);
        }
        return args;
    }
}
