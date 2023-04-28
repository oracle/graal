/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.preview.panama.hosted;

import static com.oracle.svm.core.util.VMError.unsupportedFeature;

import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.preview.panama.core.DowncallStubsHolder;
import com.oracle.svm.preview.panama.core.NativeEntryPointInfo;

import jdk.internal.foreign.abi.CapturableState;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

@Platforms(Platform.HOSTED_ONLY.class)
class DowncallStub extends NonBytecodeMethod {
    public static Signature createSignature(MetaAccessProvider metaAccess) {
        return SimpleSignature.fromKinds(new JavaKind[]{JavaKind.Object}, JavaKind.Object, metaAccess);
    }

    private record CaptureMethod(Class<?> clazz, String name) {
    }

    private static final Map<CapturableState, CaptureMethod> CAPTURE_METHODS = Map.of(
                    CapturableState.ERRNO, new CaptureMethod(LibC.class, "errno"));

    private final NativeEntryPointInfo nep;

    DowncallStub(NativeEntryPointInfo nep, MetaAccessProvider metaAccess) {
        super(
                        DowncallStubsHolder.stubName(nep),
                        true,
                        metaAccess.lookupJavaType(DowncallStubsHolder.class),
                        createSignature(metaAccess),
                        DowncallStubsHolder.getConstantPool(metaAccess));
        this.nep = nep;
        checkSignature(nep.linkMethodType());
    }

    @Override
    @SuppressWarnings("try")
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method, purpose);
        FrameStateBuilder state = kit.getFrameState();
        boolean deoptimizationTarget = MultiMethod.isDeoptTarget(method);
        List<ValueNode> arguments = kit.loadArguments(getSignature().toParameterTypes(null));

        assert arguments.size() == 1;
        arguments = convertArguments(kit, nep.linkMethodType(), arguments.get(0));

        ValueNode callAddress = arguments.remove(nep.callAddressIndex());
        ValueNode captureAddress = null;
        if (nep.capturesCallState()) {
            assert nep.captureAddressIndex() > nep.callAddressIndex();
            // We already removed 1 element from the list, so we must offset the index by one as
            // well
            captureAddress = arguments.remove(nep.captureAddressIndex() - 1);
        }

        state.clearLocals();
        SubstrateCallingConventionType cc = SubstrateCallingConventionKind.Native.toType(true)
                        .withParametersAssigned(nep.parametersAssignment())
                        .withReturnSaving(nep.returnsAssignment()); // Assignment might be null, in
                                                                    // which case this is a no-op
        ValueNode returnValue = kit.createCFunctionCall(
                        callAddress,
                        arguments,
                        SimpleSignature.fromMethodType(nep.stubMethodType(), kit.getMetaAccess()),
                        VMThreads.StatusSupport.getNewThreadStatus(CFunction.Transition.TO_NATIVE),
                        deoptimizationTarget,
                        cc);

        returnValue = adaptReturnValue(kit, nep.linkMethodType(), returnValue);

        /*
         * Note that we might need to make this stub uninterrruptible as to prevent anything from
         * altering the captured state before it is captured
         */
        if (captureAddress != null) {
            int index = 0;

            // Relies on values() yielding the capturable states in the correct (yet unspecified)
            // order
            for (var capture : CapturableState.values()) {
                if (nep.requiresCapture(capture)) {
                    if (!CAPTURE_METHODS.containsKey(capture)) {
                        throw unsupportedFeature("Capturing call state of " + capture + " is not supported.");
                    }
                    if (index != 0) {
                        // It should work out of the box, but disabled until tested (only used on
                        // Windows)
                        throw unsupportedFeature("Capturing more than one state is not supported.");
                    }

                    var captureMethodInfo = CAPTURE_METHODS.get(capture);

                    // This should boil down to a native call
                    state.clearStack();
                    InvokeNode captured = kit.createInvoke(captureMethodInfo.clazz, captureMethodInfo.name, CallTargetNode.InvokeKind.Static, state, kit.bci());
                    captured.setInlineControl(Invoke.InlineControl.Never); // I don't know why, but
                                                                           // that is apparently
                                                                           // required

                    AddressNode writeAddress = kit.append(new OffsetAddressNode(captureAddress, ConstantNode.forIntegerBits(64, index)));
                    kit.emitWrite(writeAddress, captured);

                    // Should offset by exactly one int
                    index += 4;
                }
            }
        }

        kit.createReturn(returnValue, JavaKind.Object);

        return kit.finalizeGraph();
    }

    private static void checkKind(JavaKind kind, String id) {
        UserError.guarantee(kind.isPrimitive(), "Only primitive types are supported; got " + kind + "@" + id);
    }

    private static void checkClass(Class<?> clazz, String id) {
        checkKind(JavaKind.fromJavaClass(clazz), id);
    }

    private static void checkSignature(MethodType signature) {
        checkClass(signature.returnType(), "ret");
        for (int i = 0; i < signature.parameterCount(); ++i) {
            checkClass(signature.parameterType(i), "param" + i);
        }
    }

    private static List<ValueNode> convertArguments(HostedGraphKit kit, MethodType signature, ValueNode argumentsArray) {
        var args = kit.liftArray(argumentsArray, JavaKind.Object, signature.parameterCount() + 1);
        assert args.size() == signature.parameterCount() + 1;
        // We have to drop the NEP, which is the last argument
        args.remove(args.size() - 1);
        for (int i = 0; i < args.size(); ++i) {
            args.set(i, kit.createUnboxing(args.get(i), JavaKind.fromJavaClass(signature.parameterType(i))));
        }
        return args;
    }

    public static ValueNode adaptReturnValue(HostedGraphKit kit, MethodType signature, ValueNode invokeValue) {
        ValueNode returnValue = invokeValue;
        JavaKind returnKind = JavaKind.fromJavaClass(signature.returnType());
        if (returnKind.equals(JavaKind.Void)) {
            return kit.createObject(null);
        }

        var boxed = kit.getMetaAccess().lookupJavaType(returnKind.toBoxedJavaClass());
        returnValue = kit.createBoxing(returnValue, returnKind, boxed);
        return returnValue;
    }
}
