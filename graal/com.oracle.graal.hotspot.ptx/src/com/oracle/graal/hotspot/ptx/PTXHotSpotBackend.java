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

import static com.oracle.graal.api.code.CallingConvention.Type.*;
import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition.*;
import static com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.*;
import static com.oracle.graal.lir.LIRValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.ptx.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotReplacementsImpl.GraphProducer;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.LIRInstruction.ValueProcedure;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.ptx.*;
import com.oracle.graal.lir.ptx.PTXMemOp.LoadReturnAddrOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.word.*;

/**
 * HotSpot PTX specific backend.
 */
public class PTXHotSpotBackend extends HotSpotBackend {

    /**
     * Descriptor for the PTX runtime method for launching a kernel. The C++ signature is:
     * 
     * <pre>
     *     jlong gpu::Ptx::execute_kernel_from_vm(JavaThread* thread, jlong kernel, jlong parametersAndReturnValueBuffer, jint parametersAndReturnValueBufferSize, jint encodedReturnTypeSize)
     * </pre>
     */
    public static final ForeignCallDescriptor LAUNCH_KERNEL = new ForeignCallDescriptor("execute_kernel_from_vm", long.class, Word.class, long.class, long.class, int.class, int.class);

    public PTXHotSpotBackend(HotSpotGraalRuntime runtime, HotSpotProviders providers) {
        super(runtime, providers);
    }

    @Override
    public boolean shouldAllocateRegisters() {
        return false;
    }

    @Override
    public void completeInitialization() {
        HotSpotHostForeignCallsProvider hostForeignCalls = (HotSpotHostForeignCallsProvider) getRuntime().getHostProviders().getForeignCalls();
        CompilerToGPU compilerToGPU = getRuntime().getCompilerToGPU();
        deviceInitialized = compilerToGPU.deviceInit();
        if (deviceInitialized) {
            long launchKernel = compilerToGPU.getLaunchKernelAddress();
            hostForeignCalls.registerForeignCall(LAUNCH_KERNEL, launchKernel, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, NOT_REEXECUTABLE, ANY_LOCATION);
        }
        super.completeInitialization();
    }

    private boolean deviceInitialized;

    @Override
    public FrameMap newFrameMap() {
        return new PTXFrameMap(getCodeCache());
    }

    public boolean isDeviceInitialized() {
        return deviceInitialized;
    }

    @Override
    public GraphProducer getGraphProducer() {
        if (!deviceInitialized) {
            // GPU could not be initialized so offload is disabled
            return null;
        }
        return new PTXGraphProducer(getRuntime().getHostBackend(), this);
    }

    static final class RegisterAnalysis extends ValueProcedure {
        private final SortedSet<Integer> signed32 = new TreeSet<>();
        private final SortedSet<Integer> signed64 = new TreeSet<>();

        // unsigned8 is only for ld, st and cbt
        private final SortedSet<Integer> unsigned8 = new TreeSet<>();
        private final SortedSet<Integer> unsigned64 = new TreeSet<>();

        // private final SortedSet<Integer> float16 = new TreeSet<>();
        private final SortedSet<Integer> float32 = new TreeSet<>();
        private final SortedSet<Integer> float64 = new TreeSet<>();

        LIRInstruction op;

        void emitDeclarations(Buffer codeBuffer) {
            for (Integer i : unsigned8) {
                codeBuffer.emitString(".reg .u8 %r" + i.intValue() + ";");
            }
            for (Integer i : signed32) {
                codeBuffer.emitString(".reg .s32 %r" + i.intValue() + ";");
            }
            for (Integer i : signed64) {
                codeBuffer.emitString(".reg .s64 %r" + i.intValue() + ";");
            }
            for (Integer i : unsigned64) {
                codeBuffer.emitString(".reg .u64 %r" + i.intValue() + ";");
            }
            for (Integer i : float32) {
                codeBuffer.emitString(".reg .f32 %r" + i.intValue() + ";");
            }
            for (Integer i : float64) {
                codeBuffer.emitString(".reg .f64 %r" + i.intValue() + ";");
            }
        }

        @Override
        public Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (isVariable(value)) {
                Variable regVal = (Variable) value;
                Kind regKind = regVal.getKind();
                if ((op instanceof LoadReturnAddrOp) && (mode == OperandMode.DEF)) {
                    unsigned64.add(regVal.index);
                } else {
                    switch (regKind) {
                        case Int:
                            // If the register was used as a wider signed type
                            // do not add it here
                            if (!signed64.contains(regVal.index)) {
                                signed32.add(regVal.index);
                            }
                            break;
                        case Long:
                            // If the register was used as a narrower signed type
                            // remove it from there and add it to wider type.
                            if (signed32.contains(regVal.index)) {
                                signed32.remove(regVal.index);
                            }
                            signed64.add(regVal.index);
                            break;
                        case Float:
                            // If the register was used as a wider signed type
                            // do not add it here
                            if (!float64.contains(regVal.index)) {
                                float32.add(regVal.index);
                            }
                            break;
                        case Double:
                            // If the register was used as a narrower signed type
                            // remove it from there and add it to wider type.
                            if (float32.contains(regVal.index)) {
                                float32.remove(regVal.index);
                            }
                            float64.add(regVal.index);
                            break;
                        case Object:
                            unsigned64.add(regVal.index);
                            break;
                        case Byte:
                            unsigned8.add(regVal.index);
                            break;
                        default:
                            throw GraalInternalError.shouldNotReachHere("unhandled register type " + value.toString());
                    }
                }
            }
            return value;
        }
    }

    class PTXFrameContext implements FrameContext {

        @Override
        public void enter(CompilationResultBuilder crb) {
            // codeBuffer.emitString(".address_size 32"); // PTX ISA version 2.3
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
        }

        public boolean hasFrame() {
            return true;
        }
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerator lirGen, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        // Omit the frame of the method:
        // - has no spill slots or other slots allocated during register allocation
        // - has no callee-saved registers
        // - has no incoming arguments passed on the stack
        // - has no instructions with debug info
        FrameMap frameMap = lirGen.frameMap;
        AbstractAssembler masm = createAssembler(frameMap);
        PTXFrameContext frameContext = new PTXFrameContext();
        CompilationResultBuilder crb = factory.createBuilder(getCodeCache(), getForeignCalls(), frameMap, masm, frameContext, compilationResult);
        crb.setFrameSize(0);
        return crb;
    }

    @Override
    protected AbstractAssembler createAssembler(FrameMap frameMap) {
        return new PTXMacroAssembler(getTarget(), frameMap.registerConfig);
    }

    @Override
    public LIRGenerator newLIRGenerator(StructuredGraph graph, FrameMap frameMap, CallingConvention cc, LIR lir) {
        return new PTXLIRGenerator(graph, getProviders(), frameMap, cc, lir);
    }

    private static void emitKernelEntry(CompilationResultBuilder crb, LIRGenerator lirGen, ResolvedJavaMethod codeCacheOwner) {
        // Emit PTX kernel entry text based on PTXParameterOp
        // instructions in the start block. Remove the instructions
        // once kernel entry text and directives are emitted to
        // facilitate seemless PTX code generation subsequently.
        assert codeCacheOwner != null : lirGen.getGraph() + " is not associated with a method";
        final String name = codeCacheOwner.getName();
        Buffer codeBuffer = crb.asm.codeBuffer;

        // Emit initial boiler-plate directives.
        codeBuffer.emitString(".version 3.0");
        codeBuffer.emitString(".target sm_30");
        codeBuffer.emitString0(".entry " + name + " (");
        codeBuffer.emitString("");

        // Get the start block
        Block startBlock = lirGen.lir.cfg.getStartBlock();
        // Keep a list of ParameterOp instructions to delete from the
        // list of instructions in the block.
        ArrayList<LIRInstruction> deleteOps = new ArrayList<>();

        // Emit .param arguments to kernel entry based on ParameterOp
        // instruction.
        for (LIRInstruction op : lirGen.lir.lir(startBlock)) {
            if (op instanceof PTXParameterOp) {
                op.emitCode(crb);
                deleteOps.add(op);
            }
        }

        // Delete ParameterOp instructions.
        for (LIRInstruction op : deleteOps) {
            lirGen.lir.lir(startBlock).remove(op);
        }

        // Start emiting body of the PTX kernel.
        codeBuffer.emitString0(") {");
        codeBuffer.emitString("");
    }

    // Emit .reg space declarations
    private static void emitRegisterDecl(CompilationResultBuilder crb, LIRGenerator lirGen, ResolvedJavaMethod codeCacheOwner) {

        assert codeCacheOwner != null : lirGen.getGraph() + " is not associated with a method";

        Buffer codeBuffer = crb.asm.codeBuffer;
        RegisterAnalysis registerAnalysis = new RegisterAnalysis();

        for (Block b : lirGen.lir.codeEmittingOrder()) {
            for (LIRInstruction op : lirGen.lir.lir(b)) {
                if (op instanceof LabelOp) {
                    // Don't consider this as a definition
                } else {
                    registerAnalysis.op = op;
                    op.forEachTemp(registerAnalysis);
                    op.forEachOutput(registerAnalysis);
                }
            }
        }

        registerAnalysis.emitDeclarations(codeBuffer);

        // emit predicate register declaration
        int maxPredRegNum = ((PTXLIRGenerator) lirGen).getNextPredRegNumber();
        if (maxPredRegNum > 0) {
            codeBuffer.emitString(".reg .pred %p<" + maxPredRegNum + ">;");
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIRGenerator lirGen, ResolvedJavaMethod codeCacheOwner) {
        assert codeCacheOwner != null : lirGen.getGraph() + " is not associated with a method";
        Buffer codeBuffer = crb.asm.codeBuffer;
        // Emit the prologue
        emitKernelEntry(crb, lirGen, codeCacheOwner);

        // Emit register declarations
        try {
            emitRegisterDecl(crb, lirGen, codeCacheOwner);
        } catch (GraalInternalError e) {
            e.printStackTrace();
            // TODO : Better error handling needs to be done once
            // all types of parameters are handled.
            codeBuffer.setPosition(0);
            codeBuffer.close(false);
            return;
        }
        // Emit code for the LIR
        try {
            crb.emit(lirGen.lir);
        } catch (GraalInternalError e) {
            e.printStackTrace();
            // TODO : Better error handling needs to be done once
            // all types of parameters are handled.
            codeBuffer.setPosition(0);
            codeBuffer.close(false);
            return;
        }

        // Emit the epilogue
        codeBuffer.emitString0("}");
        codeBuffer.emitString("");
    }
}
