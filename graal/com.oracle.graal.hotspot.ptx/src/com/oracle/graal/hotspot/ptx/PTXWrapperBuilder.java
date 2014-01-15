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
import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.asm.NumUtil.*;
import static com.oracle.graal.hotspot.ptx.PTXHotSpotBackend.*;
import static com.oracle.graal.hotspot.ptx.PTXWrapperBuilder.LaunchArg.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.ConstantNode.*;
import static java.lang.reflect.Modifier.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.ptx.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

/**
 * Utility for building a graph that "wraps" the PTX binary compiled for a method. Such a wrapper
 * handles the transition from the host CPU to the GPU and back. The graph created is something like
 * the following pseudo code with UPPER CASE denoting compile-time constants:
 * 
 * <pre>
 *     T kernel(p0, p1, ..., pN) {
 *         jint bufSize = SIZE_OF_ALIGNED_PARAMS_AND_RETURN_VALUE_WITH_PADDING(p0, p1, ..., pN);
 *         jbyte buf[bufSize] = {p0, PAD(p1), p1, ..., PAD(pN), pN};
 *         jlong result = PTX_LAUNCH_KERNEL(THREAD_REGISTER, KERNEL_ENTRY_POINT, dimX, dimY, dimZ, buf, bufSize, encodedReturnTypeSize);
 *         return convert(result);
 *     }
 * </pre>
 */
public class PTXWrapperBuilder extends GraphKit {

    /**
     * The incoming Java arguments to the method.
     */
    ParameterNode[] javaParameters;

    /**
     * The size of the buffer holding the kernel parameters and the extra word for storing the
     * pointer to device memory for the return value.
     * 
     * @see LaunchArg#ParametersAndReturnValueBufferSize
     */
    int bufSize;

    /**
     * Offset of each Java argument in the kernel parameters buffer.
     */
    int[] javaParameterOffsetsInKernelParametersBuffer;

    /**
     * Constants denoting the arguments to {@link PTXHotSpotBackend#LAUNCH_KERNEL}.
     */
    enum LaunchArg {
        Thread, Kernel, DimX, DimY, DimZ, ParametersAndReturnValueBuffer, ParametersAndReturnValueBufferSize, EncodedReturnTypeSize
    }

    /**
     * Creates the graph implementing the CPU to GPU transition.
     * 
     * @param method a method that has been compiled to GPU binary code
     * @param kernelAddress the entry point of the GPU binary for {@code kernelMethod}
     */
    public PTXWrapperBuilder(ResolvedJavaMethod method, long kernelAddress, HotSpotProviders providers) {
        super(new StructuredGraph(method), providers);
        int wordSize = providers.getCodeCache().getTarget().wordSize;
        Kind wordKind = providers.getCodeCache().getTarget().wordKind;
        Signature sig = method.getSignature();
        boolean isStatic = isStatic(method.getModifiers());
        int sigCount = sig.getParameterCount(false);
        javaParameters = new ParameterNode[(!isStatic ? 1 : 0) + sigCount];
        javaParameterOffsetsInKernelParametersBuffer = new int[javaParameters.length];
        int javaParametersIndex = 0;
        Kind returnKind = sig.getReturnKind();

        BitSet objects = new BitSet();
        if (!isStatic) {
            allocateParameter(Kind.Object, javaParametersIndex++, objects, wordSize);
        }
        for (int sigIndex = 0; sigIndex < sigCount; sigIndex++) {
            Kind kind = sig.getParameterKind(sigIndex);
            allocateParameter(kind, javaParametersIndex++, objects, wordSize);
        }
        bufSize = roundUp(bufSize, wordSize);

        // Add slot for holding pointer to device memory storing return value
        int encodedReturnTypeSize = 0;
        if (returnKind != Kind.Void) {
            bufSize += wordSize;
            if (returnKind == Kind.Object) {
                encodedReturnTypeSize = -wordSize;
            } else {
                encodedReturnTypeSize = returnKind.getBitCount() / Byte.SIZE;
            }
        }

        AllocaNode buf = append(new AllocaNode(bufSize / wordSize, objects));

        Map<LaunchArg, ValueNode> args = new EnumMap<>(LaunchArg.class);
        args.put(Thread, append(new ReadRegisterNode(providers.getRegisters().getThreadRegister(), true, false)));
        args.put(Kernel, ConstantNode.forLong(kernelAddress, getGraph()));
        args.put(DimX, forInt(1, getGraph()));
        args.put(DimY, forInt(1, getGraph()));
        args.put(DimZ, forInt(1, getGraph()));
        args.put(ParametersAndReturnValueBuffer, buf);
        args.put(ParametersAndReturnValueBufferSize, forInt(bufSize, getGraph()));
        args.put(EncodedReturnTypeSize, forInt(encodedReturnTypeSize, getGraph()));

        int sigIndex = isStatic ? 0 : -1;
        for (javaParametersIndex = 0; javaParametersIndex < javaParameters.length; javaParametersIndex++) {
            ParameterNode javaParameter = javaParameters[javaParametersIndex];
            int javaParameterOffset = javaParameterOffsetsInKernelParametersBuffer[javaParametersIndex];
            LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, javaParameter.kind(), javaParameterOffset, getGraph());
            append(new WriteNode(buf, javaParameter, location, BarrierType.NONE, false, false));
            updateDimArg(method, providers, sig, sigIndex++, args, javaParameter);
        }
        if (returnKind != Kind.Void) {
            LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, wordKind, bufSize - wordSize, getGraph());
            append(new WriteNode(buf, ConstantNode.forIntegerKind(wordKind, 0L, getGraph()), location, BarrierType.NONE, false, false));
        }

        FrameStateBuilder fsb = new FrameStateBuilder(method, getGraph(), true);
        FrameState fs = fsb.create(0);
        getGraph().start().setStateAfter(fs);

        ValueNode[] launchArgsArray = args.values().toArray(new ValueNode[args.size()]);
        ForeignCallNode result = append(new ForeignCallNode(providers.getForeignCalls(), LAUNCH_KERNEL, launchArgsArray));
        result.setDeoptimizationState(fs);

        ConstantNode isObjectResultArg = ConstantNode.forBoolean(returnKind == Kind.Object, getGraph());
        InvokeNode handlePendingException = createInvoke(getClass(), "handlePendingException", args.get(Thread), isObjectResultArg);
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
                getObjectResult = createInvoke(getClass(), "getObjectResult", args.get(Thread));
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

    /**
     * Allocates a slot in the kernel parameters' buffer for a Java parameter.
     * 
     * @param kind the kind of the parameter
     * @param javaParametersIndex the index of the Java parameter
     */
    private void allocateParameter(Kind kind, int javaParametersIndex, BitSet objects, int wordSize) {
        int kindByteSize = kind == Kind.Object ? wordSize : kind.getBitCount() / Byte.SIZE;
        bufSize = roundUp(bufSize, kindByteSize);
        javaParameterOffsetsInKernelParametersBuffer[javaParametersIndex] = bufSize;
        Stamp stamp;
        if (kind == Kind.Object) {
            stamp = StampFactory.object();
            int slot = bufSize / wordSize;
            objects.set(slot);
        } else {
            stamp = StampFactory.forKind(kind);
        }
        javaParameters[javaParametersIndex] = unique(new ParameterNode(javaParametersIndex, stamp));
        bufSize += kindByteSize;
    }

    /**
     * Updates the {@code dimX}, {@code dimY} or {@code dimZ} argument passed to the kernel if
     * {@code javaParameter} is annotated with {@link ParallelOver}.
     */
    private void updateDimArg(ResolvedJavaMethod method, HotSpotProviders providers, Signature sig, int sigIndex, Map<LaunchArg, ValueNode> launchArgs, ParameterNode javaParameter) {
        if (sigIndex >= 0) {
            ParallelOver parallelOver = getParameterAnnotation(ParallelOver.class, sigIndex, method);
            if (parallelOver != null && sig.getParameterType(sigIndex, method.getDeclaringClass()).equals(providers.getMetaAccess().lookupJavaType(int[].class))) {
                ArrayLengthNode dimension = append(new ArrayLengthNode(javaParameter));
                LaunchArg argKey = LaunchArg.valueOf(LaunchArg.class, "Dim" + parallelOver.dimension());
                ValueNode existing = launchArgs.put(argKey, dimension);
                if (existing != null && existing instanceof ArrayLengthNode) {
                    throw new GraalInternalError("@" + ParallelOver.class.getSimpleName() + " with dimension=" + parallelOver.dimension() + " applied to multiple parameters");
                }
            }
        }
    }

    /**
     * Snippet invoked upon return from the kernel to handle any pending exceptions.
     */
    @Snippet
    private static void handlePendingException(Word thread, boolean isObjectResult) {
        if (clearPendingException(thread)) {
            if (isObjectResult) {
                getAndClearObjectResult(thread);
            }
            DeoptimizeNode.deopt(DeoptimizationAction.None, RuntimeConstraint);
        }
    }

    /**
     * Snippet invoked upon return from the kernel to retrieve an object return value from the
     * thread local used for communicating object return values from VM calls.
     */
    @Snippet
    private static Object getObjectResult(Word thread) {
        return getAndClearObjectResult(thread);
    }
}
