/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.CallingConvention.Type.*;
import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.compiler.GraalCompiler.*;

import java.lang.reflect.*;
import java.util.*;

import com.amd.okra.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.hsail.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.hsail.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

/**
 * HSAIL specific backend.
 */
public class HSAILHotSpotBackend extends HotSpotBackend {

    private Map<String, String> paramTypeMap = new HashMap<>();
    private final boolean deviceInitialized;

    public HSAILHotSpotBackend(HotSpotGraalRuntime runtime, HotSpotProviders providers) {
        super(runtime, providers);
        paramTypeMap.put("HotSpotResolvedPrimitiveType<int>", "s32");
        paramTypeMap.put("HotSpotResolvedPrimitiveType<float>", "f32");
        paramTypeMap.put("HotSpotResolvedPrimitiveType<double>", "f64");
        paramTypeMap.put("HotSpotResolvedPrimitiveType<long>", "s64");

        // The order of the conjunction below is important: the OkraUtil
        // call may provision the native library required by the initialize() call
        deviceInitialized = OkraUtil.okraLibExists() && initialize();
    }

    @Override
    public boolean shouldAllocateRegisters() {
        return true;
    }

    /**
     * Initializes the GPU device.
     * 
     * @return whether or not initialization was successful
     */
    private static native boolean initialize();

    /**
     * Determines if the GPU device (or simulator) is available and initialized.
     */
    public boolean isDeviceInitialized() {
        return deviceInitialized;
    }

    /**
     * Completes the initialization of the HSAIL backend. This includes initializing the providers
     * and registering any method substitutions specified by the HSAIL backend.
     */
    @Override
    public void completeInitialization() {
        final HotSpotProviders providers = getProviders();
        HotSpotVMConfig config = getRuntime().getConfig();
        // Initialize the lowering provider.
        final HotSpotLoweringProvider lowerer = (HotSpotLoweringProvider) providers.getLowerer();
        lowerer.initialize(providers, config);

        // Register the replacements used by the HSAIL backend.
        HSAILHotSpotReplacementsImpl replacements = (HSAILHotSpotReplacementsImpl) providers.getReplacements();
        replacements.completeInitialization();
    }

    /**
     * Compiles and installs a given method to a GPU binary.
     */
    public HotSpotNmethod compileAndInstallKernel(Method method) {
        ResolvedJavaMethod javaMethod = getProviders().getMetaAccess().lookupJavaMethod(method);
        return installKernel(javaMethod, compileKernel(javaMethod, true));
    }

    /**
     * Compiles a given method to HSAIL code.
     * 
     * @param makeBinary specifies whether a GPU binary should also be generated for the HSAIL code.
     *            If true, the returned value is guaranteed to have a non-zero
     *            {@linkplain ExternalCompilationResult#getEntryPoint() entry point}.
     * @return the HSAIL code compiled from {@code method}'s bytecode
     */
    public ExternalCompilationResult compileKernel(ResolvedJavaMethod method, boolean makeBinary) {
        StructuredGraph graph = new StructuredGraph(method);
        HotSpotProviders providers = getProviders();
        new GraphBuilderPhase.Instance(providers.getMetaAccess(), GraphBuilderConfiguration.getEagerDefault(), OptimisticOptimizations.ALL).apply(graph);
        PhaseSuite<HighTierContext> graphBuilderSuite = providers.getSuites().getDefaultGraphBuilderSuite();
        graphBuilderSuite.appendPhase(new NonNullParametersPhase());
        CallingConvention cc = getCallingConvention(providers.getCodeCache(), Type.JavaCallee, graph.method(), false);
        Suites suites = providers.getSuites().getDefaultSuites();
        ExternalCompilationResult hsailCode = compileGraph(graph, cc, method, providers, this, this.getTarget(), null, graphBuilderSuite, OptimisticOptimizations.NONE, getProfilingInfo(graph), null,
                        suites, true, new ExternalCompilationResult(), CompilationResultBuilderFactory.Default);

        if (makeBinary) {
            if (!deviceInitialized) {
                throw new GraalInternalError("Cannot generate GPU kernel if device is not initialized");
            }
            try (Scope ds = Debug.scope("GeneratingKernelBinary")) {
                long kernel = generateKernel(hsailCode.getTargetCode(), method.getName());
                if (kernel == 0) {
                    throw new GraalInternalError("Failed to compile HSAIL kernel");
                }
                hsailCode.setEntryPoint(kernel);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }
        return hsailCode;
    }

    /**
     * Generates a GPU binary from HSAIL code.
     */
    private static native long generateKernel(byte[] hsailCode, String name);

    /**
     * Installs the {@linkplain ExternalCompilationResult#getEntryPoint() GPU binary} associated
     * with some given HSAIL code in the code cache and returns a {@link HotSpotNmethod} handle to
     * the installed code.
     * 
     * @param hsailCode HSAIL compilation result for which a GPU binary has been generated
     * @return a handle to the binary as installed in the HotSpot code cache
     */
    public final HotSpotNmethod installKernel(ResolvedJavaMethod method, ExternalCompilationResult hsailCode) {
        assert hsailCode.getEntryPoint() != 0L;
        return getProviders().getCodeCache().addExternalMethod(method, hsailCode);
    }

    public boolean executeKernel(HotSpotInstalledCode kernel, int jobSize, Object[] args) throws InvalidInstalledCodeException {
        if (!deviceInitialized) {
            throw new GraalInternalError("Cannot execute GPU kernel if device is not initialized");
        }
        return executeKernel0(kernel, jobSize, args);
    }

    private static native boolean executeKernel0(HotSpotInstalledCode kernel, int jobSize, Object[] args) throws InvalidInstalledCodeException;

    /**
     * Use the HSAIL register set when the compilation target is HSAIL.
     */
    @Override
    public FrameMap newFrameMap() {
        return new HSAILFrameMap(getCodeCache());
    }

    @Override
    public LIRGenerator newLIRGenerator(StructuredGraph graph, FrameMap frameMap, CallingConvention cc, LIR lir) {
        return new HSAILHotSpotLIRGenerator(graph, getProviders(), getRuntime().getConfig(), frameMap, cc, lir);
    }

    class HotSpotFrameContext implements FrameContext {

        public boolean hasFrame() {
            return true;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            Debug.log("Nothing to do here");
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            Debug.log("Nothing to do here");
        }
    }

    @Override
    protected AbstractAssembler createAssembler(FrameMap frameMap) {
        return new HSAILAssembler(getTarget());
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerator lirGen, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        FrameMap frameMap = lirGen.frameMap;
        AbstractAssembler masm = createAssembler(frameMap);
        HotSpotFrameContext frameContext = new HotSpotFrameContext();
        CompilationResultBuilder crb = factory.createBuilder(getCodeCache(), getForeignCalls(), frameMap, masm, frameContext, compilationResult);
        crb.setFrameSize(frameMap.frameSize());
        return crb;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIRGenerator lirGen, ResolvedJavaMethod method) {
        assert method != null : lirGen.getGraph() + " is not associated with a method";
        // Emit the prologue.
        AbstractAssembler asm = crb.asm;
        asm.emitString0("version 0:95: $full : $large;");
        asm.emitString("");

        Signature signature = method.getSignature();
        int sigParamCount = signature.getParameterCount(false);
        // We're subtracting 1 because we're not making the final gid as a parameter.

        int nonConstantParamCount = sigParamCount - 1;
        boolean isStatic = (Modifier.isStatic(method.getModifiers()));
        // Determine if this is an object lambda.
        boolean isObjectLambda = true;

        if (signature.getParameterType(nonConstantParamCount, null).getKind() == Kind.Int) {
            isObjectLambda = false;
        } else {
            // Add space for gid int reg.
            nonConstantParamCount++;
        }

        // If this is an instance method, include mappings for the "this" parameter
        // as the first parameter.
        if (!isStatic) {
            nonConstantParamCount++;
        }
        // Add in any "constant" parameters (currently none).
        int totalParamCount = nonConstantParamCount;
        JavaType[] paramtypes = new JavaType[totalParamCount];
        String[] paramNames = new String[totalParamCount];
        int pidx = 0;
        MetaAccessProvider metaAccess = getProviders().getMetaAccess();
        for (int i = 0; i < totalParamCount; i++) {
            if (i == 0 && !isStatic) {
                paramtypes[i] = metaAccess.lookupJavaType(Object.class);
                paramNames[i] = "%_this";
            } else if (i < nonConstantParamCount) {
                if (isObjectLambda && (i == (nonConstantParamCount))) {
                    // Set up the gid register mapping.
                    paramtypes[i] = metaAccess.lookupJavaType(int.class);
                    paramNames[i] = "%_gid";
                } else {
                    paramtypes[i] = signature.getParameterType(pidx++, null);
                    paramNames[i] = "%_arg" + i;
                }
            }
        }

        asm.emitString0("// " + (isStatic ? "static" : "instance") + " method " + method);
        asm.emitString("");
        asm.emitString0("kernel &run (");
        asm.emitString("");

        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.registerConfig;
        // Build list of param types which does include the gid (for cc register mapping query).
        JavaType[] ccParamTypes = new JavaType[nonConstantParamCount + 1];
        // Include the gid.
        System.arraycopy(paramtypes, 0, ccParamTypes, 0, nonConstantParamCount);

        // Last entry is always int (its register gets used in the workitemabsid instruction)
        // this is true even for object stream labmdas
        if (sigParamCount > 0) {
            ccParamTypes[ccParamTypes.length - 1] = metaAccess.lookupJavaType(int.class);
        }
        CallingConvention cc = regConfig.getCallingConvention(JavaCallee, null, ccParamTypes, getTarget(), false);

        /**
         * Compute the hsail size mappings up to but not including the last non-constant parameter
         * (which is the gid).
         * 
         */
        String[] paramHsailSizes = new String[totalParamCount];
        for (int i = 0; i < totalParamCount; i++) {
            String paramtypeStr = paramtypes[i].toString();
            String sizeStr = paramTypeMap.get(paramtypeStr);
            // Catch all for any unmapped paramtype that is u64 (address of an object).
            paramHsailSizes[i] = (sizeStr != null ? sizeStr : "u64");
        }
        // Emit the kernel function parameters.
        for (int i = 0; i < totalParamCount; i++) {
            String str = "align 8 kernarg_" + paramHsailSizes[i] + " " + paramNames[i];

            if (i != totalParamCount - 1) {
                str += ",";
            }
            asm.emitString(str);
        }
        asm.emitString(") {");

        /*
         * End of parameters start of prolog code. Emit the load instructions for loading of the
         * kernel non-constant parameters into registers. The constant class parameters will not be
         * loaded up front but will be loaded as needed.
         */
        for (int i = 0; i < nonConstantParamCount; i++) {
            asm.emitString("ld_kernarg_" + paramHsailSizes[i] + "  " + HSAIL.mapRegister(cc.getArgument(i)) + ", [" + paramNames[i] + "];");
        }

        /*
         * Emit the workitemaid instruction for loading the hidden gid parameter. This is assigned
         * the register as if it were the last of the nonConstant parameters.
         */
        String workItemReg = "$s" + Integer.toString(asRegister(cc.getArgument(nonConstantParamCount)).encoding());
        asm.emitString("workitemabsid_u32 " + workItemReg + ", 0;");

        /*
         * Note the logic used for this spillseg size is to leave space and then go back and patch
         * in the correct size once we have generated all the instructions. This should probably be
         * done in a more robust way by implementing something like codeBuffer.insertString.
         */
        int spillsegDeclarationPosition = asm.position() + 1;
        String spillsegTemplate = "align 4 spill_u8 %spillseg[123456];";
        asm.emitString(spillsegTemplate);
        // Emit object array load prologue here.
        if (isObjectLambda) {
            boolean useCompressedOops = getRuntime().getConfig().useCompressedOops;
            final int arrayElementsOffset = HotSpotGraalRuntime.getArrayBaseOffset(Kind.Object);
            String iterationObjArgReg = HSAIL.mapRegister(cc.getArgument(nonConstantParamCount - 1));
            // iterationObjArgReg will be the highest $d register in use (it is the last parameter)
            // so tempReg can be the next higher $d register
            String tmpReg = "$d" + (asRegister(cc.getArgument(nonConstantParamCount - 1)).encoding() + 1);
            // Convert gid to long.
            asm.emitString("cvt_u64_s32 " + tmpReg + ", " + workItemReg + "; // Convert gid to long");
            // Adjust index for sizeof ref. Where to pull this size from?
            asm.emitString("mul_u64 " + tmpReg + ", " + tmpReg + ", " + (useCompressedOops ? 4 : 8) + "; // Adjust index for sizeof ref");
            // Adjust for actual data start.
            asm.emitString("add_u64 " + tmpReg + ", " + tmpReg + ", " + arrayElementsOffset + "; // Adjust for actual elements data start");
            // Add to array ref ptr.
            asm.emitString("add_u64 " + tmpReg + ", " + tmpReg + ", " + iterationObjArgReg + "; // Add to array ref ptr");
            // Load the object into the parameter reg.
            if (useCompressedOops) {

                // Load u32 into the d 64 reg since it will become an object address
                asm.emitString("ld_global_u32 " + tmpReg + ", " + "[" + tmpReg + "]" + "; // Load compressed ptr from array");

                long narrowOopBase = getRuntime().getConfig().narrowOopBase;
                long narrowOopShift = getRuntime().getConfig().narrowOopShift;

                if (narrowOopBase == 0 && narrowOopShift == 0) {
                    // No more calculation to do, mov to target register
                    asm.emitString("mov_b64 " + iterationObjArgReg + ", " + tmpReg + "; // no shift or base addition");
                } else {
                    if (narrowOopBase == 0) {
                        asm.emitString("shl_u64 " + iterationObjArgReg + ", " + tmpReg + ", " + narrowOopShift + "; // do narrowOopShift");
                    } else if (narrowOopShift == 0) {
                        asm.emitString("add_u64 " + iterationObjArgReg + ", " + tmpReg + ", " + narrowOopBase + "; // add narrowOopBase");
                    } else {
                        asm.emitString("mad_u64 " + iterationObjArgReg + ", " + tmpReg + ", " + (1 << narrowOopShift) + ", " + narrowOopBase + "; // shift and add narrowOopBase");
                    }
                }

            } else {
                asm.emitString("ld_global_u64 " + iterationObjArgReg + ", " + "[" + tmpReg + "]" + "; // Load from array element into parameter reg");
            }
        }
        // Prologue done, Emit code for the LIR.
        crb.emit(lirGen.lir);
        // Now that code is emitted go back and figure out what the upper Bound stack size was.
        long maxStackSize = ((HSAILAssembler) crb.asm).upperBoundStackSize();
        String spillsegStringFinal;
        if (maxStackSize == 0) {
            // If no spilling, get rid of spillseg declaration.
            char[] array = new char[spillsegTemplate.length()];
            Arrays.fill(array, ' ');
            spillsegStringFinal = new String(array);
        } else {
            spillsegStringFinal = spillsegTemplate.replace("123456", String.format("%6d", maxStackSize));
        }
        asm.emitString(spillsegStringFinal, spillsegDeclarationPosition);

        // Emit the epilogue.
        asm.emitString0("};");
        asm.emitString("");
    }
}
