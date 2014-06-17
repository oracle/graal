/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.hsail;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.GraalInternalError;
import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.gpu.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.printer.*;

/**
 * Implements compile and dispatch of Java code containing lambda constructs. Currently only used by
 * JDK interception code that offloads to the GPU.
 */
public class ForEachToGraal implements CompileAndDispatch {

    private static HSAILHotSpotBackend getHSAILBackend() {
        Backend backend = runtime().getBackend(HSAIL.class);
        return (HSAILHotSpotBackend) backend;
    }

    ConcurrentHashMap<Class<?>, String> resolvedConsumerTargetMethods = new ConcurrentHashMap<>();

    /**
     * Returns the name of the reduction method given a class implementing {@link IntConsumer}.
     *
     * @param opClass a class implementing {@link IntConsumer}.
     * @return the name of the reduction method
     */
    public String getIntReduceTargetName(Class<?> opClass) {
        String cachedMethodName = resolvedConsumerTargetMethods.get(Objects.requireNonNull(opClass));
        if (cachedMethodName != null) {
            return cachedMethodName;
        } else {
            Method acceptMethod = null;
            for (Method m : opClass.getMethods()) {
                if (m.getName().equals("applyAsInt")) {
                    assert acceptMethod == null : "found more than one implementation of applyAsInt in " + opClass;
                    acceptMethod = m;
                }
            }
            // Ensure a debug configuration for this thread is initialized
            if (DebugScope.getConfig() == null) {
                DebugEnvironment.initialize(System.out);
            }

            HSAILHotSpotBackend backend = getHSAILBackend();
            Providers providers = backend.getProviders();
            StructuredGraph graph = new StructuredGraph(((HotSpotMetaAccessProvider) providers.getMetaAccess()).lookupJavaMethod(acceptMethod));
            new GraphBuilderPhase.Instance(providers.getMetaAccess(), GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL).apply(graph);
            NodeIterable<MethodCallTargetNode> calls = graph.getNodes(MethodCallTargetNode.class);
            assert calls.count() == 1;
            ResolvedJavaMethod lambdaMethod = calls.first().targetMethod();
            Debug.log("target ... %s", lambdaMethod);

            String className = lambdaMethod.getDeclaringClass().getName();
            if (!className.equals("Ljava/lang/Integer;")) {
                return null;
            }
            resolvedConsumerTargetMethods.put(opClass, lambdaMethod.getName());
            return lambdaMethod.getName().intern();
        }
    }

    /**
     * Gets a compiled and installed kernel for the lambda called by the
     * {@link IntConsumer#accept(int)} method in a class implementing {@link IntConsumer}.
     *
     * @param intConsumerClass a class implementing {@link IntConsumer}
     * @return a {@link HotSpotNmethod} handle to the compiled and installed kernel
     */
    private static HotSpotNmethod getCompiledLambda(Class<?> intConsumerClass) {
        Method acceptMethod = null;
        for (Method m : intConsumerClass.getMethods()) {
            if (m.getName().equals("accept")) {
                assert acceptMethod == null : "found more than one implementation of accept(int) in " + intConsumerClass;
                acceptMethod = m;
            }
        }

        // Ensure a debug configuration for this thread is initialized
        if (DebugScope.getConfig() == null) {
            DebugEnvironment.initialize(System.out);
        }

        HSAILHotSpotBackend backend = getHSAILBackend();
        Providers providers = backend.getProviders();
        StructuredGraph graph = new StructuredGraph(((HotSpotMetaAccessProvider) providers.getMetaAccess()).lookupJavaMethod(acceptMethod));
        new GraphBuilderPhase.Instance(providers.getMetaAccess(), GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL).apply(graph);
        NodeIterable<MethodCallTargetNode> calls = graph.getNodes(MethodCallTargetNode.class);
        assert calls.count() == 1;
        ResolvedJavaMethod lambdaMethod = calls.first().targetMethod();
        Debug.log("target ... %s", lambdaMethod);

        if (lambdaMethod == null) {
            Debug.log("Did not find call in accept()");
            return null;
        }
        assert lambdaMethod.getName().startsWith("lambda$");

        ExternalCompilationResult hsailCode = backend.compileKernel(lambdaMethod, true);
        return backend.installKernel(lambdaMethod, hsailCode);
    }

    @Override
    public Object createKernel(Class<?> consumerClass) {
        try {
            return getCompiledLambda(consumerClass);
        } catch (Throwable e) {
            // If Graal compilation throws an exception, we want to revert to regular Java
            Debug.log("WARNING: Graal compilation failed");
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Object createKernelFromHsailString(String code, String methodName) {
        ExternalCompilationResult hsailCode = new ExternalCompilationResult();
        try (Debug.Scope ds = Debug.scope("GeneratingKernelBinary")) {

            HSAILHotSpotBackend backend = getHSAILBackend();
            Providers providers = backend.getProviders();
            Method integerOffloadMethod = null;

            for (Method m : Integer.class.getMethods()) {
                if (m.getName().equals(methodName)) {
                    integerOffloadMethod = m;
                    break;
                }
            }
            if (integerOffloadMethod != null) {
                ResolvedJavaMethod rm = ((HotSpotMetaAccessProvider) providers.getMetaAccess()).lookupJavaMethod(integerOffloadMethod);

                long kernel = HSAILHotSpotBackend.generateKernel(code.getBytes(), "Integer::" + methodName);
                if (kernel == 0) {
                    throw new GraalInternalError("Failed to compile HSAIL kernel from String");
                }
                hsailCode.setEntryPoint(kernel);
                return backend.installKernel(rm, hsailCode); // is a HotSpotNmethod
            } else {
                return null;
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    @Override
    public boolean dispatchKernel(Object kernel, int jobSize, Object[] args) {
        HotSpotNmethod code = (HotSpotNmethod) kernel;
        if (code != null) {
            try {
                // No return value from HSAIL kernels
                getHSAILBackend().executeKernel(code, jobSize, args);
                return true;
            } catch (InvalidInstalledCodeException iice) {
                Debug.log("WARNING: Invalid installed code at exec time: %s", iice);
                iice.printStackTrace();
                return false;
            }
        } else {
            // Should throw something sensible here
            return false;
        }
    }

    /**
     * Running with a larger global size seems to increase the performance for sum, but it might be
     * different for other reductions so it is a knob.
     */
    private static final int GlobalSize = 1024 * Integer.getInteger("com.amd.sumatra.reduce.globalsize.multiple", 1);

    @Override
    public Integer offloadIntReduceImpl(Object okraKernel, int identity, int[] streamSource) {
        // NOTE - this reduce requires local size of 64 which is the SumatraUtils default

        // Handmade reduce does not support +UseCompressedOops
        HotSpotVMConfig config = runtime().getConfig();
        if (config.useCompressedOops == true || config.useHSAILDeoptimization == true) {
            throw new GraalInternalError("Reduce offload not compatible with +UseCompressedOops or +UseHSAILDeoptimization");
        }

        try {
            assert streamSource.length >= GlobalSize : "Input array length=" + streamSource.length + " smaller than requested global_size=" + GlobalSize;

            int result[] = {identity};
            Object args[] = {streamSource, result, streamSource.length};
            args[0] = streamSource;

            dispatchKernel(okraKernel, GlobalSize, args);

            // kernel result is result[0].
            return result[0];
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getIntegerReduceIntrinsic(String reducerName) {

        // Note all of these depend on group size of 256

        String reduceOp = "/* Invalid */ ";
        String atomicResultProduction = "/* Invalid */ ";
        if (reducerName.equals("sum")) {
            reduceOp = "add_u32 ";
            atomicResultProduction = "atomicnoret_add_global_u32 ";
        } else if (reducerName.equals("max")) {
            reduceOp = "max_s32 ";
            atomicResultProduction = "atomicnoret_max_global_s32 ";
        } else if (reducerName.equals("min")) {
            reduceOp = "min_s32 ";
            atomicResultProduction = "atomicnoret_min_global_s32 ";
        } else {
            return "/* Invalid */ ";
        }

        // @formatter:off
        return new String(
                "version 0:95:$full:$large; // BRIG Object Format Version 0:4" + "\n"
                + "" + "\n"
                + "kernel &run(" + "\n"
                + "	align 8 kernarg_u64 %arg_p3," + "\n"
                + "	align 8 kernarg_u64 %arg_p4," + "\n"
                + "	align 4 kernarg_u32 %arg_p5)" + "\n"
                + "{" + "\n"
                + "" + "\n"
                + "	align 4 group_u32 %reduce_cllocal_scratch[256];" + "\n"
                + "" + "\n"
                + "	workitemabsid_u32 $s2, 0;" + "\n"
                + "" + "\n"
                + "	ld_kernarg_u32	$s1, [%arg_p5];" + "\n"
                + "	ld_kernarg_u64	$d0, [%arg_p4];" + "\n"
                + "	ld_kernarg_u64	$d1, [%arg_p3];" + "\n"
                + "" + "\n"
                + "	add_u64 $d0, $d0, 24;             // adjust over obj array headers" + "\n"
                + "	add_u64 $d1, $d1, 24;" + "\n"
                + "	cmp_ge_b1_s32	$c0, $s2, $s1; // if(gloId < length){" + "\n"
                + "	cbr	$c0, @BB0_1;" + "\n"
                + "	gridsize_u32	$s0, 0;        // s0 is globalsize" + "\n"
                + " add_u32 $s0, $s0, $s2;         // gx += globalsize" + "\n"
                + "	cvt_s64_s32	$d2, $s2;      // s2 is global id" + "\n"
                + "	shl_u64	$d2, $d2, 2;" + "\n"
                + "	add_u64	$d2, $d1, $d2;" + "\n"
                + "	ld_global_u32	$s3, [$d2];    // load this element from input" + "\n"
                + "	brn	@BB0_3;" + "\n"
                + "" + "\n"
                + "@BB0_1:" + "\n"
                + "	mov_b32	$s0, $s2;" + "\n"                                  + "" + "\n"
                + "@BB0_3:" + "\n"
                + "	cmp_ge_b1_s32	$c1, $s0, $s1; // while (gx < length)" + "\n"
                + "	cbr	$c1, @BB0_6;" + "\n"
                + "	gridsize_u32	$s2, 0;" + "\n"
                + "" + "\n"
                + "@BB0_5:" + "\n"
                + "	cvt_s64_s32	$d2, $s0;" + "\n"
                + "	shl_u64	$d2, $d2, 2;" + "\n"
                + "	add_u64	$d2, $d1, $d2;" + "\n"
                + "	ld_global_u32	$s4, [$d2];" + "\n"
                +       reduceOp + "  $s3, $s3, $s4;" + "\n"
                + "	add_u32	$s0, $s0, $s2;" + "\n"
                + "	cmp_lt_b1_s32	$c1, $s0, $s1;" + "\n"
                + "	cbr	$c1, @BB0_5;" + "\n"
                + "" + "\n"
                + "@BB0_6:" + "\n"
                + "	workgroupid_u32	$s0, 0;" + "\n"
                + "	workgroupsize_u32	$s2, 0;" + "\n"
                + "	mul_u32	$s2, $s2, $s0;" + "\n"
                + "	sub_u32	$s2, $s1, $s2;" + "\n"
                + "	workitemid_u32	$s1, 0;" + "\n"
                + "	add_u32	$s4, $s1, 128;"
                + "\n"
                + "	cmp_lt_b1_u32	$c1, $s4, $s2;" + "\n"
                + "	cmp_lt_b1_s32	$c2, $s1, 128;" + "\n"
                + "	and_b1	$c1, $c2, $c1;" + "\n"
                + "	cvt_s64_s32	$d1, $s1;" + "\n"
                + "	shl_u64	$d1, $d1, 2;" + "\n"
                + "	lda_group_u64	$d2, [%reduce_cllocal_scratch];" + "\n"
                + "	add_u64	$d1, $d2, $d1;" + "\n"
                + "	st_group_u32	$s3, [$d1];" + "\n"
                + "	barrier_fgroup;" + "\n"
                + "	not_b1	$c1, $c1;" + "\n"
                + "	cbr	$c1, @BB0_8;" + "\n"
                + "	ld_group_u32	$s3, [$d1];" + "\n"
                + "	cvt_s64_s32	$d3, $s4;" + "\n"
                + "	shl_u64	$d3, $d3, 2;" + "\n"
                + "	add_u64	$d3, $d2, $d3;" + "\n"
                + "	ld_group_u32	$s4, [$d3];" + "\n"
                +       reduceOp + "  $s3, $s3, $s4;" + "\n"
                + "	st_group_u32	$s3, [$d1];" + "\n"
                + "" + "\n"
                + "@BB0_8:" + "\n"
                + "	add_u32	$s3, $s1, 64;" + "\n"
                + "	cmp_lt_b1_u32	$c1, $s3, $s2;" + "\n"
                + "	cmp_lt_b1_s32	$c2, $s1, 64;" + "\n"
                + "	and_b1	$c1, $c2, $c1;" + "\n"
                + "	barrier_fgroup;" + "\n"
                + "	not_b1	$c1, $c1;" + "\n"
                + "	cbr	$c1, @BB0_10;" + "\n"
                + "	ld_group_u32	$s4, [$d1];" + "\n"
                + "	cvt_s64_s32	$d3, $s3;" + "\n"
                + "	shl_u64	$d3, $d3, 2;" + "\n"
                + "	add_u64	$d3, $d2, $d3;" + "\n"
                + "	ld_group_u32	$s3, [$d3];" + "\n"
                +       reduceOp + "  $s3, $s3, $s4;"
                + "\n"
                + "	st_group_u32	$s3, [$d1];" + "\n"
                + "" + "\n"
                + "@BB0_10:" + "\n"
                + "	add_u32	$s3, $s1, 32;" + "\n"
                + "	cmp_lt_b1_u32	$c1, $s3, $s2;" + "\n"
                + "	cmp_lt_b1_s32	$c2, $s1, 32;" + "\n"
                + "	and_b1	$c1, $c2, $c1;" + "\n"
                + "	barrier_fgroup;" + "\n"
                + "	not_b1	$c1, $c1;" + "\n"
                + "	cbr	$c1, @BB0_12;" + "\n"
                + "	ld_group_u32	$s4, [$d1];" + "\n"
                + "	cvt_s64_s32	$d3, $s3;" + "\n"
                + "	shl_u64	$d3, $d3, 2;" + "\n"
                + "	add_u64	$d3, $d2, $d3;" + "\n"
                + "	ld_group_u32	$s3, [$d3];" + "\n"
                +       reduceOp + "  $s3, $s3, $s4;" + "\n"
                + "	st_group_u32	$s3, [$d1];" + "\n"
                + "" + "\n"
                + "@BB0_12:" + "\n"
                + "	add_u32	$s3, $s1, 16;" + "\n"
                + "	cmp_lt_b1_u32	$c1, $s3, $s2;" + "\n"
                + "	cmp_lt_b1_s32	$c2, $s1, 16;" + "\n"
                + "	and_b1	$c1, $c2, $c1;" + "\n"
                + "	barrier_fgroup;" + "\n"
                + "	not_b1	$c1, $c1;" + "\n"
                + "	cbr	$c1, @BB0_14;" + "\n"
                + "	ld_group_u32	$s4, [$d1];" + "\n"
                + "	cvt_s64_s32	$d3, $s3;" + "\n"
                + "	shl_u64	$d3, $d3, 2;" + "\n"
                + "	add_u64	$d3, $d2, $d3;" + "\n"
                + "	ld_group_u32	$s3, [$d3];" + "\n"
                +       reduceOp + "  $s3, $s3, $s4;" + "\n"
                + "	st_group_u32	$s3, [$d1];" + "\n"
                + "" + "\n"
                + "@BB0_14:" + "\n"
                + "	add_u32	$s3, $s1, 8;" + "\n"
                + "	cmp_lt_b1_u32	$c1, $s3, $s2;" + "\n"
                + "	cmp_lt_b1_s32	$c2, $s1, 8;" + "\n"
                + "	and_b1	$c1, $c2, $c1;" + "\n"
                + "	barrier_fgroup;" + "\n"
                + "	not_b1	$c1, $c1;" + "\n"
                + "	cbr	$c1, @BB0_16;" + "\n"
                + "	ld_group_u32	$s4, [$d1];" + "\n"
                + "	cvt_s64_s32	$d3, $s3;" + "\n"
                + "	shl_u64	$d3, $d3, 2;" + "\n"
                + "	add_u64	$d3, $d2, $d3;" + "\n"
                + "	ld_group_u32	$s3, [$d3];" + "\n"
                +       reduceOp + "  $s3, $s3, $s4;" + "\n"
                + "	st_group_u32	$s3, [$d1];" + "\n"
                + "" + "\n"
                + "@BB0_16:" + "\n"
                + "	add_u32	$s3, $s1, 4;" + "\n"
                + "	cmp_lt_b1_u32	$c1, $s3, $s2;" + "\n"
                + "	cmp_lt_b1_s32	$c2, $s1, 4;" + "\n"
                + "	and_b1	$c1, $c2, $c1;" + "\n"
                + "	barrier_fgroup;" + "\n"
                + "	not_b1	$c1, $c1;" + "\n"
                + "	cbr	$c1, @BB0_18;" + "\n"
                + "	ld_group_u32	$s4, [$d1];" + "\n"
                + "	cvt_s64_s32	$d3, $s3;" + "\n"
                + "	shl_u64	$d3, $d3, 2;" + "\n"
                + "	add_u64	$d3, $d2, $d3;" + "\n"
                + "	ld_group_u32	$s3, [$d3];" + "\n"
                +       reduceOp + "  $s3, $s3, $s4;" + "\n"
                + "	st_group_u32	$s3, [$d1];" + "\n"
                + "" + "\n"
                + "@BB0_18:" + "\n"
                + "	add_u32	$s3, $s1, 2;" + "\n"
                + "	cmp_lt_b1_u32	$c1, $s3, $s2;" + "\n"
                + "	cmp_lt_b1_s32	$c2, $s1, 2;" + "\n"
                + "	and_b1	$c1, $c2, $c1;" + "\n"
                + "	barrier_fgroup;" + "\n"
                + "	not_b1	$c1, $c1;" + "\n"
                + "	cbr	$c1, @BB0_20;" + "\n"
                + "	ld_group_u32	$s4, [$d1];" + "\n"
                + "	cvt_s64_s32	$d3, $s3;" + "\n"
                + "	shl_u64	$d3, $d3, 2;" + "\n"
                + "	add_u64	$d3, $d2, $d3;" + "\n"
                + "	ld_group_u32	$s3, [$d3];" + "\n"
                +       reduceOp + "  $s3, $s3, $s4;" + "\n"
                + "	st_group_u32	$s3, [$d1];" + "\n"
                + "" + "\n"
                + "@BB0_20:" + "\n"
                + "	add_u32	$s3, $s1, 1;" + "\n"
                + "	cmp_lt_b1_u32	$c1, $s3, $s2;" + "\n"
                + "	cmp_lt_b1_s32	$c2, $s1, 1;" + "\n"
                + "	and_b1	$c1, $c2, $c1;" + "\n"
                + "	barrier_fgroup;" + "\n"
                + "	not_b1	$c1, $c1;" + "\n"
                + "	cbr	$c1, @BB0_22;" + "\n"
                + "	ld_group_u32	$s4, [$d1];" + "\n"
                + "	cvt_s64_s32	$d3, $s3;" + "\n"
                + "	shl_u64	$d3, $d3, 2;" + "\n"
                + "	add_u64	$d2, $d2, $d3;" + "\n"
                + "	ld_group_u32	$s3, [$d2];" + "\n"
                +       reduceOp + "  $s3, $s3, $s4;" + "\n"
                + "	st_group_u32	$s3, [$d1];" + "\n"
                + "" + "\n"
                + "@BB0_22:" + "\n"
                + "	cmp_gt_b1_u32	$c0, $s1, 0;  // s1 is local id, done if > 0" + "\n"
                + "	cbr	$c0, @BB0_24;" + "\n"
                + "" + "\n"
                + "	ld_group_u32	$s2, [%reduce_cllocal_scratch];  // s2 is result[get_group_id(0)];" + "\n"
                +       atomicResultProduction + " [$d0], $s2; // build global result from local results" + "\n"
                + "" + "\n"
                + "@BB0_24:" + "\n"
                + "	ret;" + "\n"
                + "};" + "\n");
        //@formatter:on
    }
}
