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

import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.asm.NumUtil.*;
import static com.oracle.graal.hotspot.ptx.PTXHotSpotBackend.*;
import static com.oracle.graal.hotspot.ptx.PTXWrapperBuilder.LaunchArg.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.ConstantNode.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.ptx.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

/**
 * Utility for building a graph that "wraps" a compiled PTX kernel. Such a wrapper handles the
 * transition from the host CPU to the GPU and back. The wrapper allocate 3 on-stack buffers:
 * <ul>
 * <li>PARAMS: a buffer for the kernel parameters and one word for the on-device address of the
 * return value (if any).</li>
 * <li>PINNED: a buffer into which the address of pinned objects is saved.</li>
 * <li>OBJECT_OFFSETS: the offsets of the object values in PARAMS.</li>
 * </ul>
 *
 *
 * The PARAMS buffer is the {@code CU_LAUNCH_PARAM_BUFFER_POINTER} buffer passed in the
 * {@code extra} argument to the {@code cuLaunchKernel} function. This buffer contains the
 * parameters to the call. The buffer is word aligned and each parameter is aligned in the buffer
 * according to its data size. The wrapper copies the incoming arguments into the buffer as is. The
 * native {@link PTXHotSpotBackend#CALL_KERNEL callKernel} function will pin the memory for each
 * object parameter (using {@code cuMemHostRegister}) and then replace the object pointer in PARAMS
 * with an on-device pointer to the object's memory (see {@code cuMemHostGetDevicePointer}). The
 * function saves pinned object pointer into PINNED so that it can unpinned once the kernel returns.
 * The object pointers in PARAMS are specified by OBJECT_OFFSETS.
 * <p>
 * As a concrete example, for a kernel whose Java method signature is:
 *
 * <pre>
 *     static int kernel(int p1, short p2, Object p3, long p4)
 * </pre>
 *
 * the graph created is shown below as psuedo-code:
 *
 * <pre>
 *     int kernel_wrapper(int p1, short p2, oop p3, long p4) {
 *         address kernelAddr = kernel.start;
 *         if (kernelAddr == 0) {
 *             deopt(InvalidateRecompile, RuntimeConstraint);
 *         }
 *         byte PARAMS[32];
 *         word PINNED[1]; // note: no refmap
 *         int OBJECT_OFFSETS[1] = {8};
 *         ((int*) PARAMS)[0] = p1;
 *         ((short*) PARAMS)[2] = p2;
 *         ((word*) PARAMS)[1] = p3;
 *         ((long*) PARAMS)[2] = p4;
 *         int result = CALL_KERNEL(THREAD_REGISTER, KERNEL_ENTRY_POINT, 1, 1, 1, PARAMS, 32, 1, OBJECT_OFFSETS, PINNED, 4);
 *         if (clearPendingException(thread)) {
 *             deopt(None, RuntimeConstraint);
 *         }
 *         return result;
 *     }
 * </pre>
 * <p>
 * The generated graph includes a reference to the {@link HotSpotNmethod} for the kernel. There must
 * be another reference to the same {@link HotSpotNmethod} object to ensure that the nmethod is not
 * unloaded by the next full GC. Currently, these extra "keep-alive" references are maintained by
 * {@link PTXHotSpotBackend}.
 * <p>
 * The PTX runtime code called by the wrapper blocks GC while the kernel is executing (cf
 * GetPrimitiveArrayCritical/ReleasePrimitiveArrayCritical JNI functions). This ensures objects can
 * be safely passed to kernels but should be replaced with a lighter weight mechanism at some point.
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
     * Constants denoting the arguments to {@link PTXHotSpotBackend#CALL_KERNEL}.
     */
    // @formatter:off
    enum LaunchArg {
        Thread,
        Kernel,
        DimX,
        DimY,
        DimZ,
        ParametersAndReturnValueBuffer,
        ParametersAndReturnValueBufferSize,
        ObjectParametersCount,
        ObjectParametersOffsets,
        PinnedObjects,
        EncodedReturnTypeSize
    }
    // @formatter:on

    /**
     * Creates the graph implementing the CPU to GPU transition.
     *
     * @param method a method that has been compiled to GPU binary code
     * @param kernel the installed GPU binary for {@code method}
     * @see PTXWrapperBuilder
     */
    public PTXWrapperBuilder(ResolvedJavaMethod method, HotSpotNmethod kernel, HotSpotProviders providers) {
        super(new StructuredGraph(method), providers);
        int wordSize = providers.getCodeCache().getTarget().wordSize;
        int intSize = Integer.SIZE / Byte.SIZE;
        Kind wordKind = providers.getCodeCache().getTarget().wordKind;
        Signature sig = method.getSignature();
        boolean isStatic = method.isStatic();
        int sigCount = sig.getParameterCount(false);
        javaParameters = new ParameterNode[(!isStatic ? 1 : 0) + sigCount];
        javaParameterOffsetsInKernelParametersBuffer = new int[javaParameters.length];
        int javaParametersIndex = 0;
        Kind returnKind = sig.getReturnKind();

        List<Integer> objectSlots = new ArrayList<>(javaParameters.length);
        if (!isStatic) {
            allocateParameter(Kind.Object, javaParametersIndex++, objectSlots, wordSize);
        }
        for (int sigIndex = 0; sigIndex < sigCount; sigIndex++) {
            Kind kind = sig.getParameterKind(sigIndex);
            allocateParameter(kind, javaParametersIndex++, objectSlots, wordSize);
        }
        bufSize = roundUp(bufSize, wordSize);

        // Add slot for the device memory pointer. The kernel writes a
        // pointer in this slot that points to the return value.
        int encodedReturnTypeSize = 0;
        if (returnKind != Kind.Void) {
            bufSize += wordSize;
            if (returnKind == Kind.Object) {
                encodedReturnTypeSize = -wordSize;
            } else {
                encodedReturnTypeSize = returnKind.getBitCount() / Byte.SIZE;
            }
        }

        InvokeNode kernelStart = createInvoke(getClass(), "getKernelStart", ConstantNode.forConstant(HotSpotObjectConstant.forObject(kernel), providers.getMetaAccess(), getGraph()));

        AllocaNode buf = append(new AllocaNode(bufSize / wordSize, new BitSet()));
        ValueNode objectParametersOffsets;
        ValueNode pinnedObjects;
        ConstantNode nullWord = ConstantNode.forIntegerKind(wordKind, 0L, getGraph());
        if (objectSlots.isEmpty()) {
            objectParametersOffsets = ConstantNode.forLong(0, getGraph());
            pinnedObjects = ConstantNode.forLong(0, getGraph());
        } else {
            int intsPerWord = wordSize / intSize;
            int slots = roundUp(objectSlots.size(), intsPerWord);
            objectParametersOffsets = append(new AllocaNode(slots, new BitSet()));
            // No refmap for pinned objects list since kernel execution is (currently) GC unsafe
            pinnedObjects = append(new AllocaNode(objectSlots.size(), new BitSet()));

            // Initialize the object parameter offsets array
            int index = 0;
            for (int slot : objectSlots) {
                int offset = slot * wordSize;
                LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, Kind.Int, index * intSize, getGraph());
                append(new WriteNode(objectParametersOffsets, ConstantNode.forInt(offset, getGraph()), location, BarrierType.NONE, false));
                index++;
            }
        }

        Map<LaunchArg, ValueNode> args = new EnumMap<>(LaunchArg.class);
        args.put(Thread, append(new ReadRegisterNode(providers.getRegisters().getThreadRegister(), true, false)));
        args.put(Kernel, kernelStart);
        args.put(DimX, forInt(1, getGraph()));
        args.put(DimY, forInt(1, getGraph()));
        args.put(DimZ, forInt(1, getGraph()));
        args.put(ParametersAndReturnValueBuffer, buf);
        args.put(ParametersAndReturnValueBufferSize, forInt(bufSize, getGraph()));
        args.put(ObjectParametersCount, forInt(objectSlots.size(), getGraph()));
        args.put(ObjectParametersOffsets, objectParametersOffsets);
        args.put(PinnedObjects, pinnedObjects);
        args.put(EncodedReturnTypeSize, forInt(encodedReturnTypeSize, getGraph()));

        int sigIndex = isStatic ? 0 : -1;
        for (javaParametersIndex = 0; javaParametersIndex < javaParameters.length; javaParametersIndex++) {
            ParameterNode javaParameter = javaParameters[javaParametersIndex];
            int javaParameterOffset = javaParameterOffsetsInKernelParametersBuffer[javaParametersIndex];
            LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, javaParameter.getKind(), javaParameterOffset, getGraph());
            append(new WriteNode(buf, javaParameter, location, BarrierType.NONE, false));
            updateDimArg(method, sig, sigIndex++, args, javaParameter);
        }
        if (returnKind != Kind.Void) {
            LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, wordKind, bufSize - wordSize, getGraph());
            append(new WriteNode(buf, nullWord, location, BarrierType.NONE, false));
        }

        HIRFrameStateBuilder fsb = new HIRFrameStateBuilder(method, getGraph(), true);
        FrameState fs = fsb.create(0);
        getGraph().start().setStateAfter(fs);

        ValueNode[] launchArgsArray = args.values().toArray(new ValueNode[args.size()]);
        ForeignCallNode result = append(new ForeignCallNode(providers.getForeignCalls(), CALL_KERNEL, launchArgsArray));
        result.setStateAfter(fs);

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
                returnValue = unique(new NarrowNode(result, 32));
                break;
            case Long:
                returnValue = result;
                break;
            case Float: {
                ValueNode asInt = unique(new NarrowNode(result, 32));
                returnValue = ReinterpretNode.reinterpret(Kind.Float, asInt);
                break;
            }
            case Double:
                returnValue = ReinterpretNode.reinterpret(Kind.Double, result);
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

        rewriteWordTypes(providers.getSnippetReflection());
        inlineInvokes(providers.getSnippetReflection());

        if (Debug.isDumpEnabled()) {
            Debug.dump(getGraph(), "Kernel launch graph before compilation");
        }
    }

    /**
     * Computes offset and size of space in PARAMS for a Java parameter.
     *
     * @param kind the kind of the parameter
     * @param javaParametersIndex the index of the Java parameter
     */
    private void allocateParameter(Kind kind, int javaParametersIndex, List<Integer> objectSlots, int wordSize) {
        int kindByteSize = kind == Kind.Object ? wordSize : kind.getBitCount() / Byte.SIZE;
        bufSize = roundUp(bufSize, kindByteSize);
        javaParameterOffsetsInKernelParametersBuffer[javaParametersIndex] = bufSize;
        Stamp stamp;
        if (kind == Kind.Object) {
            stamp = StampFactory.object();
            int slot = bufSize / wordSize;
            objectSlots.add(slot);
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
    private void updateDimArg(ResolvedJavaMethod method, Signature sig, int sigIndex, Map<LaunchArg, ValueNode> launchArgs, ParameterNode javaParameter) {
        if (sigIndex >= 0) {
            ParallelOver parallelOver = method.getParameterAnnotation(ParallelOver.class, sigIndex);
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
     * Snippet invoked to get the {@linkplain HotSpotNmethod#getStart() entry point} of the kernel,
     * deoptimizing if the kernel is invalid.
     */
    @Snippet
    private static long getKernelStart(HotSpotNmethod ptxKernel) {
        long start = ptxKernel.getStart();
        if (start == 0L) {
            DeoptimizeNode.deopt(InvalidateRecompile, RuntimeConstraint);
        }
        return start;
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
