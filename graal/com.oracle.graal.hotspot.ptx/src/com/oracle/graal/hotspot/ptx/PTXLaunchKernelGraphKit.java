/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.ptx;

import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.asm.NumUtil.*;
import static com.oracle.graal.hotspot.ptx.PTXHotSpotBackend.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static java.lang.reflect.Modifier.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

/**
 * Utility for building a graph for launching a PTX kernel compiled for a method. This graph created
 * is something like the following pseudo code:
 * 
 * <pre>
 *     jlong kernel(p0, p1, ..., pN) {
 *         jint kernelParamsBufSize = SIZE_OF_ALIGNED_PARAMS_WITH_PADDING(p0, p1, ..., pN);
 *         jbyte kernelParamsBuf[kernelParamsBufSize] = {p0, PAD(p1), p1, ..., PAD(pN), pN};
 *         jlong result = PTX_LAUNCH_KERNEL(THREAD_REGISTER, kernelParamsBuf, kernelParamsBuf);
 *         return result;
 *     }
 * </pre>
 */
public class PTXLaunchKernelGraphKit extends GraphKit {

    /**
     * The incoming Java arguments to the kernel invocation.
     */
    ParameterNode[] javaParameters;

    /**
     * The size of the buffer holding the parameters and the extra word for storing the pointer to
     * device memory for the return value. This will be the same as
     * PTXKernelArguments::device_argument_buffer_size().
     */
    int kernelParametersAndReturnValueBufferSize;

    /**
     * Offsets of each Java argument in the parameters buffer.
     */
    int[] javaParameterOffsetsInKernelParametersBuffer;

    /**
     * Creates a graph implementing the transition from Java to the native routine that launches
     * some compiled PTX code.
     * 
     * @param kernelMethod a method that has been compiled to PTX kernel code
     * @param kernelAddress the address of the installed PTX code for {@code kernelMethod}
     */
    public PTXLaunchKernelGraphKit(ResolvedJavaMethod kernelMethod, long kernelAddress, HotSpotProviders providers) {
        super(new StructuredGraph(kernelMethod), providers);
        int wordSize = providers.getCodeCache().getTarget().wordSize;
        Kind wordKind = providers.getCodeCache().getTarget().wordKind;
        Signature sig = kernelMethod.getSignature();
        boolean isStatic = isStatic(kernelMethod.getModifiers());
        int sigCount = sig.getParameterCount(false);
        javaParameters = new ParameterNode[(!isStatic ? 1 : 0) + sigCount];
        javaParameterOffsetsInKernelParametersBuffer = new int[javaParameters.length];
        int javaParametersIndex = 0;
        Kind returnKind = sig.getReturnKind();

        BitSet objects = new BitSet();
        if (!isStatic) {
            javaParameters[javaParametersIndex] = unique(new ParameterNode(javaParametersIndex, StampFactory.declaredNonNull(kernelMethod.getDeclaringClass())));
            kernelParametersAndReturnValueBufferSize += wordSize;
            javaParameterOffsetsInKernelParametersBuffer[javaParametersIndex++] = 0;
            objects.set(0);
        }
        for (int i = 0; i < sigCount; i++) {
            Kind kind = sig.getParameterKind(i);
            int kindByteSize = kind.getBitCount() / Byte.SIZE;
            while ((kernelParametersAndReturnValueBufferSize % kindByteSize) != 0) {
                kernelParametersAndReturnValueBufferSize++;
            }
            javaParameterOffsetsInKernelParametersBuffer[javaParametersIndex] = kernelParametersAndReturnValueBufferSize;
            Stamp stamp;
            if (kind == Kind.Object) {
                stamp = StampFactory.object();
                int slot = kernelParametersAndReturnValueBufferSize / wordSize;
                objects.set(slot);
            } else {
                stamp = StampFactory.forKind(kind);
            }
            ParameterNode param = unique(new ParameterNode(javaParametersIndex, stamp));
            javaParameters[javaParametersIndex++] = param;
            kernelParametersAndReturnValueBufferSize += kindByteSize;
        }
        kernelParametersAndReturnValueBufferSize = roundUp(kernelParametersAndReturnValueBufferSize, wordSize);

        // Add slot for holding pointer to device memory storing return value
        int encodedReturnTypeSize = 0;
        if (returnKind != Kind.Void) {
            kernelParametersAndReturnValueBufferSize += wordSize;
            if (returnKind == Kind.Object) {
                encodedReturnTypeSize = -wordSize;
            } else {
                encodedReturnTypeSize = returnKind.getBitCount() / Byte.SIZE;
            }
        }

        ReadRegisterNode threadArg = append(new ReadRegisterNode(providers.getRegisters().getThreadRegister(), true, false));
        ConstantNode kernelAddressArg = ConstantNode.forLong(kernelAddress, getGraph());
        AllocaNode kernelParametersAndReturnValueBufferArg = append(new AllocaNode(kernelParametersAndReturnValueBufferSize / wordSize, objects));
        ConstantNode kernelParametersAndReturnValueBufferSizeArg = ConstantNode.forInt(kernelParametersAndReturnValueBufferSize, getGraph());
        ConstantNode encodedReturnTypeSizeArg = ConstantNode.forInt(encodedReturnTypeSize, getGraph());

        for (javaParametersIndex = 0; javaParametersIndex < javaParameters.length; javaParametersIndex++) {
            ParameterNode javaParameter = javaParameters[javaParametersIndex];
            int javaParameterOffset = javaParameterOffsetsInKernelParametersBuffer[javaParametersIndex];
            LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, javaParameter.kind(), javaParameterOffset, getGraph());
            append(new WriteNode(kernelParametersAndReturnValueBufferArg, javaParameter, location, BarrierType.NONE, false, false));
        }
        if (returnKind != Kind.Void) {
            LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, wordKind, kernelParametersAndReturnValueBufferSize - wordSize, getGraph());
            append(new WriteNode(kernelParametersAndReturnValueBufferArg, ConstantNode.forIntegerKind(wordKind, 0L, getGraph()), location, BarrierType.NONE, false, false));
        }

        FrameStateBuilder fsb = new FrameStateBuilder(kernelMethod, getGraph(), true);
        FrameState fs = fsb.create(0);
        getGraph().start().setStateAfter(fs);

        ForeignCallNode result = append(new ForeignCallNode(providers.getForeignCalls(), LAUNCH_KERNEL, threadArg, kernelAddressArg, kernelParametersAndReturnValueBufferArg,
                        kernelParametersAndReturnValueBufferSizeArg, encodedReturnTypeSizeArg));
        result.setDeoptimizationState(fs);

        ConstantNode isObjectResultArg = ConstantNode.forBoolean(returnKind == Kind.Object, getGraph());
        InvokeNode handlePendingException = createInvoke(getClass(), "handlePendingException", threadArg, isObjectResultArg);
        handlePendingException.setStateAfter(fs);
        InvokeNode getObjectResult = null;

        ValueNode returnValue;
        switch (returnKind) {
            case Void:
                returnValue = null;
                break;
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
                returnValue = unique(new ConvertNode(Kind.Long, Kind.Int, result));
                break;
            case Long:
                returnValue = result;
                break;
            case Float:
            case Double:
                returnValue = unique(new ReinterpretNode(returnKind, result));
                break;
            case Object:
                getObjectResult = createInvoke(getClass(), "getObjectResult", threadArg);
                returnValue = append(getObjectResult);
                break;
            default:
                throw new GraalInternalError("%s return kind not supported", returnKind);
        }

        append(new ReturnNode(returnValue));

        if (Debug.isDumpEnabled()) {
            Debug.dump(getGraph(), "Initial kernel launch graph");
        }

        rewriteWordTypes();
        inlineInvokes();

        if (Debug.isDumpEnabled()) {
            Debug.dump(getGraph(), "Kernel launch graph before compilation");
        }
    }

    public static void handlePendingException(Word thread, boolean isObjectResult) {
        if (clearPendingException(thread)) {
            if (isObjectResult) {
                getAndClearObjectResult(thread);
            }
            DeoptimizeNode.deopt(DeoptimizationAction.None, RuntimeConstraint);
        }
    }

    public static Object getObjectResult(Word thread) {
        return getAndClearObjectResult(thread);
    }
}
