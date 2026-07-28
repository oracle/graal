/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.jni.CallVariant;
import com.oracle.svm.core.jni.JNIJavaCallVariantWrapperHolder;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.hosted.code.EntryPointCallStubMethod;
import com.oracle.svm.interpreter.InterpreterStubSection;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * Builds runtime-class-loading JNI call wrappers that dispatch {@code jmethodID} values to the
 * interpreter.
 *
 * The generated graph mirrors the regular JNI Java-call wrappers, but routes runtime-loaded methods
 * through {@link InterpreterStubSection} entry points because their bytecode is interpreted rather
 * than compiled into image code.
 */
public final class JNIJavaCallInterpreterWrapperMethod extends EntryPointCallStubMethod {
    /** The JNI call variant implemented by this wrapper. */
    private final CallVariant callVariant;

    /** Indicates whether this wrapper implements a non-virtual JNI call variant. */
    private final boolean nonVirtual;

    /**
     * Creates a wrapper for {@code callVariant} and {@code nonVirtual} using
     * {@code originalMetaAccess} and {@code wordTypes} to model the native ABI signature.
     */
    public JNIJavaCallInterpreterWrapperMethod(CallVariant callVariant, boolean nonVirtual, MetaAccessProvider originalMetaAccess, jdk.graal.compiler.word.WordTypes wordTypes) {
        super(createName(callVariant, nonVirtual),
                        originalMetaAccess.lookupJavaType(JNIJavaCallVariantWrapperHolder.class),
                        createSignature(originalMetaAccess, wordTypes, nonVirtual),
                        JNIJavaCallVariantWrapperHolder.getConstantPool(originalMetaAccess));
        this.callVariant = callVariant;
        this.nonVirtual = nonVirtual;
    }

    /** Creates the synthetic wrapper method name for {@code callVariant} and {@code nonVirtual}. */
    private static String createName(CallVariant callVariant, boolean nonVirtual) {
        return "invokeInterpreter_" + callVariant.name() + (nonVirtual ? "_Nonvirtual" : "");
    }

    /**
     * Creates the synthetic native ABI signature for {@code nonVirtual} wrappers.
     *
     * The signature uses the word kind from {@code wordTypes} for all native pointer-like parameters
     * and returns the raw {@code long} value produced by {@link InterpreterStubSection}.
     */
    private static Signature createSignature(MetaAccessProvider originalMetaAccess, WordTypes wordTypes, boolean nonVirtual) {
        JavaKind wordKind = wordTypes.getWordKind();
        List<JavaKind> args = new ArrayList<>();
        args.add(wordKind); // JNIEnv
        args.add(wordKind); // receiver/class handle
        if (nonVirtual) {
            args.add(wordKind); // clazz handle, ignored like the regular JNIJavaCallVariantWrapperMethod path
        }
        args.add(wordKind); // runtime jmethodID handle
        args.add(wordKind); // payload: InterpreterAccessStubData, jvalue*, or va_list
        return ResolvedSignature.fromKinds(args.toArray(JavaKind[]::new), JavaKind.Long, originalMetaAccess);
    }

    /**
     * Determines whether the platform ABI requires the array stub that reads floats as doubles, see
     * {@code JNIJavaCallVariantWrapperMethod#loadArguments}. On Windows and iOS,
     * {@link CallVariant#VA_LIST} is identical to {@link CallVariant#ARRAY}. On Darwin AArch64,
     * {@link CallVariant#VARARGS} stores values as an array in the caller's stack frame, so the
     * trampoline passes the caller stack pointer as the payload.
     */
    private static boolean needsArrayReadDoubleForFloatStub(CallVariant callVariant) {
        return (Platform.includedIn(Platform.DARWIN_AARCH64.class) && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) ||
                        (Platform.includedIn(InternalPlatform.WINDOWS_BASE.class) && callVariant == CallVariant.VA_LIST);
    }

    /**
     * Builds the graph that enters the isolate, invokes the matching interpreter entry point, and
     * restores the pending JNI exception state.
     */
    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);
        JavaKind wordKind = kit.getWordTypes().getWordKind();
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
        ValueNode payload = kit.loadLocal(slotIndex, wordKind);

        kit.invokeJNIEnterIsolate(env);
        ValueNode formerPendingException = kit.invokeGetAndClearPendingException();
        String interpreterStubNamePrefix;
        if (needsArrayReadDoubleForFloatStub(callVariant)) {
            interpreterStubNamePrefix = "enterDirectInterpreterStubArrayReadDoubleForFloat";
        } else {
            interpreterStubNamePrefix = switch (callVariant) {
                case ARRAY -> "enterDirectInterpreterStubArray";
                case VARARGS -> "enterDirectInterpreterStubVarargs";
                case VA_LIST -> "enterDirectInterpreterStubVaList";
            };
        }
        String interpreterStubName = interpreterStubNamePrefix + (nonVirtual ? "NonVirtual" : "Virtual");
        ResolvedJavaMethod targetMethod = kit.findMethod(InterpreterStubSection.class, interpreterStubName, JNIObjectHandle.class, JNIObjectHandle.class, Pointer.class);
        StampPair returnStamp = StampPair.createSingle(StampFactory.forKind(JavaKind.Long));
        CallTargetNode callTarget = kit.createMethodCallTarget(InvokeKind.Static, targetMethod, new ValueNode[]{receiverOrClassHandle, methodId, payload}, returnStamp, kit.bci());

        int invokeBci = kit.bci();
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(callTarget, kit.getFrameState(), invokeBci);
        kit.noExceptionPart();
        kit.invokeSetPendingException(formerPendingException);
        kit.exceptionPart();
        kit.invokeSetPendingException(kit.exceptionObject());
        AbstractMergeNode invokeMerge = kit.endInvokeWithException();

        ValueNode exceptionValue = kit.unique(ConstantNode.defaultForKind(JavaKind.Long));
        ValueNode returnValue = kit.getGraph().addWithoutUnique(new ValuePhiNode(invoke.stamp(NodeView.DEFAULT), invokeMerge, invoke, exceptionValue));
        kit.getFrameState().push(JavaKind.Long, returnValue);
        invokeMerge.setStateAfter(kit.getFrameState().create(invokeBci, invokeMerge));
        kit.getFrameState().pop(JavaKind.Long);

        kit.invokeJNILeaveIsolate();
        kit.createReturn(returnValue, JavaKind.Long);
        return kit.finalizeGraph();
    }
}
