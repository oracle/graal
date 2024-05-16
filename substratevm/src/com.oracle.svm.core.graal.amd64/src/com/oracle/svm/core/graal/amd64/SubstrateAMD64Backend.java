/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_DECD_RSP;
import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_END;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unsupportedFeature;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.differentRegisters;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.amd64.AMD64ArithmeticLIRGenerator;
import org.graalvm.compiler.core.amd64.AMD64LIRGenerator;
import org.graalvm.compiler.core.amd64.AMD64LIRKindTool;
import org.graalvm.compiler.core.amd64.AMD64MoveFactory;
import org.graalvm.compiler.core.amd64.AMD64MoveFactoryBase;
import org.graalvm.compiler.core.amd64.AMD64NodeLIRBuilder;
import org.graalvm.compiler.core.amd64.AMD64NodeMatchRules;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.memory.MemoryExtendKind;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.CompressibleConstant;
import org.graalvm.compiler.core.gen.DebugInfoBuilder;
import org.graalvm.compiler.core.gen.LIRGenerationProvider;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.amd64.AMD64BreakpointOp;
import org.graalvm.compiler.lir.amd64.AMD64Call;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.BranchOp;
import org.graalvm.compiler.lir.amd64.AMD64FrameMap;
import org.graalvm.compiler.lir.amd64.AMD64FrameMapBuilder;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.amd64.AMD64Move;
import org.graalvm.compiler.lir.amd64.AMD64Move.MoveFromConstOp;
import org.graalvm.compiler.lir.amd64.AMD64Move.PointerCompressionOp;
import org.graalvm.compiler.lir.amd64.AMD64PrefetchOp;
import org.graalvm.compiler.lir.amd64.AMD64ReadProcid;
import org.graalvm.compiler.lir.amd64.AMD64ReadTimestampCounterWithProcid;
import org.graalvm.compiler.lir.amd64.AMD64VZeroUpper;
import org.graalvm.compiler.lir.amd64.EndbranchOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.EntryPointDecorator;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.framemap.FrameMapBuilderTool;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.gen.MoveFactory;
import org.graalvm.compiler.lir.gen.MoveFactory.BackupSlotProvider;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.HIRBlock;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.NodeValueMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.AddressLoweringByNodePhase;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.amd64.AMD64IntrinsicStubs;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.amd64.AMD64CPUFeatureAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.cpufeature.Stubs;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.graal.code.AssignedLocation;
import com.oracle.svm.core.graal.code.PatchConsumerFactory;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.code.SubstrateCompiledCode;
import com.oracle.svm.core.graal.code.SubstrateDataBuilder;
import com.oracle.svm.core.graal.code.SubstrateDebugInfoBuilder;
import com.oracle.svm.core.graal.code.SubstrateLIRGenerator;
import com.oracle.svm.core.graal.code.SubstrateNodeLIRBuilder;
import com.oracle.svm.core.graal.lir.VerificationMarkerOp;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.graal.nodes.ComputedIndirectCallTargetNode;
import com.oracle.svm.core.graal.nodes.ComputedIndirectCallTargetNode.Computation;
import com.oracle.svm.core.graal.nodes.ComputedIndirectCallTargetNode.FieldLoad;
import com.oracle.svm.core.graal.nodes.ComputedIndirectCallTargetNode.FieldLoadIfZero;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.SubstrateReferenceMapBuilder;
import com.oracle.svm.core.meta.CompressedNullConstant;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class SubstrateAMD64Backend extends SubstrateBackend implements LIRGenerationProvider {

    protected static CompressEncoding getCompressEncoding() {
        return ImageSingletons.lookup(CompressEncoding.class);
    }

    public SubstrateAMD64Backend(Providers providers) {
        super(providers);
    }

    /**
     * Returns {@code true} if a call from run-time compiled code to AOT compiled code is an AVX-SSE
     * transition. For AOT compilations, this always returns {@code false}.
     */
    @SuppressWarnings("unlikely-arg-type")
    public static boolean runtimeToAOTIsAvxSseTransition(TargetDescription target) {
        if (SubstrateUtil.HOSTED) {
            // hosted does not need to care about this
            return false;
        }
        if (!AMD64CPUFeatureAccess.canUpdateCPUFeatures()) {
            // same CPU features as hosted
            return false;
        }
        var arch = (AMD64) target.arch;
        var hostedCPUFeatures = ImageSingletons.lookup(CPUFeatureAccess.class).buildtimeCPUFeatures();
        var runtimeCPUFeatures = arch.getFeatures();
        return !hostedCPUFeatures.contains(AVX) && runtimeCPUFeatures.contains(AVX);
    }

    @Opcode("CALL_DIRECT")
    public static class SubstrateAMD64DirectCallOp extends AMD64Call.DirectCallOp {
        public static final LIRInstructionClass<SubstrateAMD64DirectCallOp> TYPE = LIRInstructionClass.create(SubstrateAMD64DirectCallOp.class);

        private final int newThreadStatus;
        @Use({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchor;
        @Temp({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchorTemp;

        private final boolean destroysCallerSavedRegisters;
        @Temp({REG, OperandFlag.ILLEGAL}) private Value exceptionTemp;

        public SubstrateAMD64DirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state,
                        Value javaFrameAnchor, Value javaFrameAnchorTemp, int newThreadStatus, boolean destroysCallerSavedRegisters, Value exceptionTemp) {
            super(TYPE, callTarget, result, parameters, temps, state);
            this.newThreadStatus = newThreadStatus;
            this.javaFrameAnchor = javaFrameAnchor;
            this.javaFrameAnchorTemp = javaFrameAnchorTemp;
            this.destroysCallerSavedRegisters = destroysCallerSavedRegisters;
            this.exceptionTemp = exceptionTemp;

            assert differentRegisters(parameters, temps, javaFrameAnchor, javaFrameAnchorTemp);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            maybeTransitionToNative(crb, masm, javaFrameAnchor, javaFrameAnchorTemp, state, newThreadStatus);
            AMD64Call.directCall(crb, masm, callTarget, null, false, state);
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return destroysCallerSavedRegisters;
        }
    }

    @Opcode("CALL_INDIRECT")
    public static class SubstrateAMD64IndirectCallOp extends AMD64Call.IndirectCallOp {
        public static final LIRInstructionClass<SubstrateAMD64IndirectCallOp> TYPE = LIRInstructionClass.create(SubstrateAMD64IndirectCallOp.class);

        private final int newThreadStatus;
        @Use({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchor;
        @Temp({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchorTemp;

        private final boolean destroysCallerSavedRegisters;
        @Temp({REG, OperandFlag.ILLEGAL}) private Value exceptionTemp;
        private final BiConsumer<CompilationResultBuilder, Integer> offsetRecorder;

        @Def({REG}) private Value[] multipleResults;

        public SubstrateAMD64IndirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress,
                        LIRFrameState state, Value javaFrameAnchor, Value javaFrameAnchorTemp, int newThreadStatus, boolean destroysCallerSavedRegisters, Value exceptionTemp,
                        BiConsumer<CompilationResultBuilder, Integer> offsetRecorder) {
            this(callTarget, result, parameters, temps, targetAddress, state, javaFrameAnchor, javaFrameAnchorTemp, newThreadStatus, destroysCallerSavedRegisters, exceptionTemp, offsetRecorder,
                            new Value[0]);
        }

        public SubstrateAMD64IndirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress,
                        LIRFrameState state, Value javaFrameAnchor, Value javaFrameAnchorTemp, int newThreadStatus, boolean destroysCallerSavedRegisters, Value exceptionTemp,
                        BiConsumer<CompilationResultBuilder, Integer> offsetRecorder, Value[] multipleResults) {
            super(TYPE, callTarget, result, parameters, temps, targetAddress, state);
            this.newThreadStatus = newThreadStatus;
            this.javaFrameAnchor = javaFrameAnchor;
            this.javaFrameAnchorTemp = javaFrameAnchorTemp;
            this.destroysCallerSavedRegisters = destroysCallerSavedRegisters;
            this.exceptionTemp = exceptionTemp;
            this.offsetRecorder = offsetRecorder;
            this.multipleResults = multipleResults;

            assert differentRegisters(parameters, temps, targetAddress, javaFrameAnchor, javaFrameAnchorTemp);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            maybeTransitionToNative(crb, masm, javaFrameAnchor, javaFrameAnchorTemp, state, newThreadStatus);
            int offset = AMD64Call.indirectCall(crb, masm, asRegister(targetAddress), callTarget, state);
            if (offsetRecorder != null) {
                offsetRecorder.accept(crb, offset);
            }
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return destroysCallerSavedRegisters;
        }
    }

    @Opcode("CALL_COMPUTED_INDIRECT")
    public static class SubstrateAMD64ComputedIndirectCallOp extends AMD64Call.MethodCallOp {
        public static final LIRInstructionClass<SubstrateAMD64ComputedIndirectCallOp> TYPE = LIRInstructionClass.create(SubstrateAMD64ComputedIndirectCallOp.class);

        // addressBase is killed during code generation
        @Use({REG}) private Value addressBase;
        @Temp({REG}) private Value addressBaseTemp;

        @Temp({REG, OperandFlag.ILLEGAL}) private Value exceptionTemp;
        private final Computation[] addressComputation;
        private final LIRKindTool lirKindTool;
        private final SharedConstantReflectionProvider constantReflection;

        public SubstrateAMD64ComputedIndirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps,
                        Value addressBase, Computation[] addressComputation,
                        LIRFrameState state, Value exceptionTemp, LIRKindTool lirKindTool, SharedConstantReflectionProvider constantReflection) {
            super(TYPE, callTarget, result, parameters, temps, state);
            this.addressBase = this.addressBaseTemp = addressBase;
            this.exceptionTemp = exceptionTemp;
            this.addressComputation = addressComputation;
            this.lirKindTool = lirKindTool;
            this.constantReflection = constantReflection;
            assert differentRegisters(parameters, temps, addressBase);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            VMError.guarantee(SubstrateOptions.SpawnIsolates.getValue(), "Memory access without isolates is not implemented");

            int compressionShift = ReferenceAccess.singleton().getCompressionShift();
            Register computeRegister = asRegister(addressBase);
            AMD64BaseAssembler.OperandSize lastOperandSize = AMD64BaseAssembler.OperandSize.get(addressBase.getPlatformKind());
            boolean nextMemoryAccessNeedsDecompress = false;

            for (var computation : addressComputation) {
                /*
                 * Both supported computations are field loads. The difference is the memory address
                 * (which either reads from the current computed value, or from a newly provided
                 * constant object).
                 */
                SharedField field;
                AMD64Address memoryAddress;
                Label done = null;

                if (computation instanceof FieldLoad) {
                    field = (SharedField) ((FieldLoad) computation).getField();
                    if (nextMemoryAccessNeedsDecompress) {
                        /*
                         * Manual implementation of the only compressed reference scheme that is
                         * currently in use: references are relative to the heap base register, with
                         * an optional shift that is known to be a valid addressing mode.
                         */
                        memoryAddress = new AMD64Address(ReservedRegisters.singleton().getHeapBaseRegister(),
                                        computeRegister, Stride.fromLog2(compressionShift),
                                        field.getOffset());
                    } else {
                        memoryAddress = new AMD64Address(computeRegister, field.getOffset());
                    }

                } else if (computation instanceof FieldLoadIfZero) {
                    done = new Label();
                    VMError.guarantee(!nextMemoryAccessNeedsDecompress, "Comparison with compressed null value not implemented");
                    masm.cmpAndJcc(lastOperandSize, computeRegister, 0, AMD64Assembler.ConditionFlag.NotEqual, done, true);

                    SubstrateObjectConstant object = (SubstrateObjectConstant) ((FieldLoadIfZero) computation).getObject();
                    field = (SharedField) ((FieldLoadIfZero) computation).getField();
                    /*
                     * Loading a field from a constant object can be expressed with a single mov
                     * instruction: the object is in the image heap, so the displacement relative to
                     * the heap base is a constant.
                     */
                    memoryAddress = new AMD64Address(ReservedRegisters.singleton().getHeapBaseRegister(),
                                    Register.None, Stride.S1,
                                    field.getOffset() + addressDisplacement(object, constantReflection),
                                    addressDisplacementAnnotation(object));

                } else {
                    throw VMError.shouldNotReachHere("Computation is not supported yet: " + computation.getClass().getTypeName());
                }

                switch (field.getStorageKind()) {
                    case Int:
                        lastOperandSize = AMD64BaseAssembler.OperandSize.DWORD;
                        nextMemoryAccessNeedsDecompress = false;
                        break;
                    case Long:
                        lastOperandSize = AMD64BaseAssembler.OperandSize.QWORD;
                        nextMemoryAccessNeedsDecompress = false;
                        break;
                    case Object:
                        lastOperandSize = AMD64BaseAssembler.OperandSize.get(lirKindTool.getNarrowOopKind().getPlatformKind());
                        nextMemoryAccessNeedsDecompress = true;
                        break;
                    default:
                        throw VMError.shouldNotReachHere("Kind is not supported yet: " + field.getStorageKind());
                }
                AMD64Assembler.AMD64RMOp.MOV.emit(masm, lastOperandSize, computeRegister, memoryAddress);

                if (done != null) {
                    masm.bind(done);
                }
            }

            VMError.guarantee(!nextMemoryAccessNeedsDecompress, "Final computed call target address is not a primitive value");
            AMD64Call.indirectCall(crb, masm, computeRegister, callTarget, state);
        }
    }

    public static Object addressDisplacementAnnotation(JavaConstant constant) {
        if (SubstrateUtil.HOSTED) {
            /*
             * AOT compilation during image generation happens before the image heap objects are
             * layouted. So the offset of the constant is not known yet during compilation time, and
             * instead needs to be patched in later. We annotate the machine code with the constant
             * that needs to be patched in.
             */
            return constant;
        } else {
            return null;
        }
    }

    public static int addressDisplacement(JavaConstant constant, SharedConstantReflectionProvider constantReflection) {
        if (SubstrateUtil.HOSTED) {
            return 0;
        } else {
            /*
             * For JIT compilation at run time, the image heap is known and immutable, so the offset
             * of the constant can be emitted immediately. No patching is required later on.
             */
            return constantReflection.getImageHeapOffset(constant);
        }
    }

    static void maybeTransitionToNative(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value javaFrameAnchor, Value temp, LIRFrameState state,
                    int newThreadStatus) {
        if (ValueUtil.isIllegal(javaFrameAnchor)) {
            /* Not a call that needs to set up a JavaFrameAnchor. */
            assert newThreadStatus == StatusSupport.STATUS_ILLEGAL;
            return;
        }
        assert StatusSupport.isValidStatus(newThreadStatus);

        Register anchor = ValueUtil.asRegister(javaFrameAnchor);
        Register lastJavaIP = ValueUtil.asRegister(temp);

        /*
         * Record the last Java instruction pointer. Note that this is actually not the return
         * address of the call, but that is fine. Patching the offset of the lea instruction would
         * be possible but more complex than just recording the reference map information twice for
         * different instructions.
         *
         * We record the instruction to load the current instruction pointer as a Call infopoint, so
         * that the same metadata is emitted in the machine code as for a normal call instruction.
         * We are already in the code emission from a single LIR instruction. So the register
         * allocator cannot interfere anymore, the reference map for the two calls is produced from
         * the same point regarding to register spilling.
         *
         * The lea loads the offset 0 relative to the end of the lea instruction, which is the same
         * as for a call instruction. So the usual AMD64 specific semantics that all the metadata is
         * registered for the end of the instruction just works.
         */
        int startPos = masm.position();
        masm.leaq(lastJavaIP, new AMD64Address(AMD64.rip));
        /*
         * We always record an indirect call, because the direct/indirect flag of the safepoint is
         * not used (the target method of the recorded call is null anyway).
         */
        crb.recordIndirectCall(startPos, masm.position(), null, state);

        KnownOffsets knownOffsets = KnownOffsets.singleton();
        masm.movq(new AMD64Address(anchor, knownOffsets.getJavaFrameAnchorLastIPOffset()), lastJavaIP);
        masm.movq(new AMD64Address(anchor, knownOffsets.getJavaFrameAnchorLastSPOffset()), AMD64.rsp);

        if (SubstrateOptions.MultiThreaded.getValue()) {
            /* Change the VMThread status from Java to Native. */
            masm.movl(new AMD64Address(ReservedRegisters.singleton().getThreadRegister(), knownOffsets.getVMThreadStatusOffset()), newThreadStatus);
        }
    }

    /**
     * Marks a point that is unreachable because a previous instruction never returns.
     */
    @Opcode("DEAD_END")
    public static class DeadEndOp extends LIRInstruction implements BlockEndOp {
        public static final LIRInstructionClass<DeadEndOp> TYPE = LIRInstructionClass.create(DeadEndOp.class);

        public DeadEndOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            if (SubstrateUtil.assertionsEnabled()) {
                ((AMD64Assembler) crb.asm).int3();
            }
        }
    }

    protected static final class SubstrateLIRGenerationResult extends LIRGenerationResult {

        private final SharedMethod method;

        public SubstrateLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, CallingConvention callingConvention,
                        RegisterAllocationConfig registerAllocationConfig, SharedMethod method) {
            super(compilationId, lir, frameMapBuilder, registerAllocationConfig, callingConvention);
            this.method = method;

            if (method.hasCalleeSavedRegisters()) {
                AMD64CalleeSavedRegisters calleeSavedRegisters = AMD64CalleeSavedRegisters.singleton();
                FrameMap frameMap = ((FrameMapBuilderTool) frameMapBuilder).getFrameMap();
                int registerSaveAreaSizeInBytes = calleeSavedRegisters.getSaveAreaSize();
                StackSlot calleeSaveArea = frameMap.allocateStackMemory(registerSaveAreaSizeInBytes, frameMap.getTarget().wordSize);

                /*
                 * The offset of the callee save area must be fixed early during image generation.
                 * It is accessed when compiling methods that have a call with callee-saved calling
                 * convention. Here we verify that offset computed earlier is the same as the offset
                 * actually reserved.
                 */
                calleeSavedRegisters.verifySaveAreaOffsetInFrame(calleeSaveArea.getRawOffset());
            }

            if (method.canDeoptimize() || method.isDeoptTarget()) {
                ((FrameMapBuilderTool) frameMapBuilder).getFrameMap().reserveOutgoing(16);
            }
        }

        public SharedMethod getMethod() {
            return method;
        }
    }

    /**
     * Inserts a {@linkplain AMD64VZeroUpper vzeroupper} instruction before calls that are an
     * AVX-SSE transition.
     *
     * The following cases are distinguished:
     *
     * First, check whether a run-time compiled method calling an AOT compiled method is an AVX-SSE
     * transition, i.e., AVX was not enabled for AOT but is enabled for JIT compilation.
     *
     * Only emit vzeroupper if the call uses a
     * {@linkplain SubstrateAMD64LIRGenerator#getDestroysCallerSavedRegisters caller-saved} calling
     * convention. For {@link StubCallingConvention stub calling convention} calls, which are
     * {@linkplain SharedMethod#hasCalleeSavedRegisters() callee-saved}, all handling is done on the
     * callee side.
     *
     * No vzeroupper is emitted for {@linkplain #isRuntimeToRuntimeCall runtime-to-runtime calls},
     * because both, the caller and the callee, have been compiled using the CPU features.
     */
    private void vzeroupperBeforeCall(SubstrateAMD64LIRGenerator gen, Value[] arguments, LIRFrameState callState, SharedMethod targetMethod) {
        // TODO maybe avoid vzeroupper if the callee does not use SSE (cf. hsLinkage.mayContainFP())
        if (runtimeToAOTIsAvxSseTransition(gen.target()) && gen.getDestroysCallerSavedRegisters(targetMethod) && !isRuntimeToRuntimeCall(callState)) {
            /*
             * We exclude the argument registers from the zeroing LIR instruction since it violates
             * the LIR semantics of @Temp that values must not be live. Note that the emitted
             * machine instruction actually zeros _all_ XMM registers which is fine since we know
             * that their upper half is not used.
             */
            gen.append(new AMD64VZeroUpper(arguments, gen.getRegisterConfig()));
        }
    }

    protected class SubstrateAMD64LIRGenerator extends AMD64LIRGenerator implements SubstrateLIRGenerator {

        public SubstrateAMD64LIRGenerator(LIRKindTool lirKindTool, AMD64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, Providers providers, LIRGenerationResult lirGenRes) {
            super(lirKindTool, arithmeticLIRGen, null, moveFactory, providers, lirGenRes);
        }

        @Override
        public void emitReturn(JavaKind kind, Value input) {
            AllocatableValue operand = Value.ILLEGAL;
            if (input != null) {
                operand = resultOperandFor(kind, input.getValueKind());
                emitMove(operand, input);
            }
            append(new AMD64ReturnOp(operand));
        }

        @Override
        public SubstrateLIRGenerationResult getResult() {
            return (SubstrateLIRGenerationResult) super.getResult();
        }

        @Override
        public SubstrateRegisterConfig getRegisterConfig() {
            return (SubstrateRegisterConfig) super.getRegisterConfig();
        }

        // @Override
        // public boolean canEliminateRedundantMoves() {
        // if (getResult().getMethod().isDeoptTarget()) {
        // /*
        // * Redundant move elimination can extend the liferanges of intervals, even over
        // * method calls. This would introduce new stack slots which are live for a method
        // * call, but not recognized during register allocation.
        // */
        // return false;
        // }
        // return true;
        // }

        protected boolean getDestroysCallerSavedRegisters(ResolvedJavaMethod targetMethod) {
            if (getResult().getMethod().isDeoptTarget()) {
                /*
                 * The Deoptimizer cannot restore register values, so in a deoptimization target
                 * method all registers must always be caller saved. It is of course inefficient to
                 * caller-save all registers and then invoke a method that callee-saves all
                 * registers again. But deoptimization entry point methods cannot be optimized
                 * aggressively anyway.
                 */
                return true;
            }
            return targetMethod == null || !((SharedMethod) targetMethod).hasCalleeSavedRegisters();
        }

        @Override
        protected Value emitIndirectForeignCallAddress(ForeignCallLinkage linkage) {
            if (!shouldEmitOnlyIndirectCalls()) {
                return null;
            }
            SubstrateForeignCallLinkage callTarget = (SubstrateForeignCallLinkage) linkage;
            SharedMethod targetMethod = (SharedMethod) callTarget.getMethod();

            Value codeOffsetInImage = emitConstant(getLIRKindTool().getWordKind(), JavaConstant.forLong(targetMethod.getCodeOffsetInImage()));
            Value codeInfo = emitJavaConstant(SubstrateObjectConstant.forObject(CodeInfoTable.getImageCodeCache()));
            Value codeStartField = new AMD64AddressValue(getLIRKindTool().getWordKind(), asAllocatable(codeInfo), KnownOffsets.singleton().getImageCodeInfoCodeStartOffset());
            Value codeStart = getArithmetic().emitLoad(getLIRKindTool().getWordKind(), codeStartField, null, MemoryOrderMode.PLAIN, MemoryExtendKind.DEFAULT);
            return getArithmetic().emitAdd(codeStart, codeOffsetInImage, false);
        }

        @Override
        protected void emitForeignCallOp(ForeignCallLinkage linkage, Value targetAddress, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
            SubstrateForeignCallLinkage callTarget = (SubstrateForeignCallLinkage) linkage;
            SharedMethod targetMethod = (SharedMethod) callTarget.getMethod();
            Value exceptionTemp = getExceptionTemp(info != null && info.exceptionEdge != null);

            vzeroupperBeforeCall(this, arguments, info, targetMethod);
            if (shouldEmitOnlyIndirectCalls()) {
                AllocatableValue targetRegister = AMD64.rax.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRKindTool()));
                emitMove(targetRegister, targetAddress);
                append(new SubstrateAMD64IndirectCallOp(targetMethod, result, arguments, temps, targetRegister, info,
                                Value.ILLEGAL, Value.ILLEGAL, StatusSupport.STATUS_ILLEGAL, getDestroysCallerSavedRegisters(targetMethod), exceptionTemp, null));
            } else {
                assert targetAddress == null;
                append(new SubstrateAMD64DirectCallOp(targetMethod, result, arguments, temps, info, Value.ILLEGAL,
                                Value.ILLEGAL, StatusSupport.STATUS_ILLEGAL, getDestroysCallerSavedRegisters(targetMethod), exceptionTemp));
            }
        }

        /**
         * For invokes that have an exception handler, the register used for the incoming exception
         * is destroyed at the call site even when registers are caller saved. The normal object
         * return register is used in {@link NodeLIRBuilder#emitReadExceptionObject} also for the
         * exception.
         */
        private Value getExceptionTemp(boolean hasExceptionEdge) {
            if (hasExceptionEdge) {
                return getRegisterConfig().getReturnRegister(JavaKind.Object).asValue();
            } else {
                return Value.ILLEGAL;
            }
        }

        @Override
        public void emitUnwind(Value operand) {
            throw shouldNotReachHere("handled by lowering");
        }

        @Override
        public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state) {
            throw shouldNotReachHere("Substrate VM does not use deoptimization");
        }

        @Override
        public void emitVerificationMarker(Object marker) {
            append(new VerificationMarkerOp(marker));
        }

        @Override
        public void emitInstructionSynchronizationBarrier() {
            throw shouldNotReachHere("AMD64 does not need instruction synchronization");
        }

        @Override
        public void emitFarReturn(AllocatableValue result, Value sp, Value ip, boolean fromMethodWithCalleeSavedRegisters) {
            append(new AMD64FarReturnOp(result, asAllocatable(sp), asAllocatable(ip), fromMethodWithCalleeSavedRegisters));
        }

        @Override
        public void emitDeadEnd() {
            append(new DeadEndOp());
        }

        @Override
        public void emitPrefetchAllocate(Value address) {
            append(new AMD64PrefetchOp(asAddressValue(address), SubstrateOptions.AllocatePrefetchInstr.getValue()));
        }

        @Override
        public Value emitCompress(Value pointer, CompressEncoding encoding, boolean isNonNull) {
            Variable result = newVariable(getLIRKindTool().getNarrowOopKind());
            boolean nonNull = useLinearPointerCompression() || isNonNull;
            append(new AMD64Move.CompressPointerOp(result, asAllocatable(pointer), ReservedRegisters.singleton().getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        }

        @Override
        public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean isNonNull) {
            assert pointer.getValueKind(LIRKind.class).getPlatformKind() == getLIRKindTool().getNarrowOopKind().getPlatformKind();
            Variable result = newVariable(getLIRKindTool().getObjectKind());
            boolean nonNull = useLinearPointerCompression() || isNonNull;
            append(new AMD64Move.UncompressPointerOp(result, asAllocatable(pointer), ReservedRegisters.singleton().getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        }

        @Override
        public void emitConvertNullToZero(AllocatableValue result, AllocatableValue value) {
            if (useLinearPointerCompression()) {
                append(new AMD64Move.ConvertNullToZeroOp(result, value));
            } else {
                emitMove(result, value);
            }
        }

        @Override
        public void emitConvertZeroToNull(AllocatableValue result, Value value) {
            if (useLinearPointerCompression()) {
                append(new AMD64Move.ConvertZeroToNullOp(result, (AllocatableValue) value));
            } else {
                emitMove(result, value);
            }
        }

        @Override
        public void emitProcid(AllocatableValue dst) {
            // GR-43733: Replace string by feature when we remove support for Java 17
            if (supportsCPUFeature("RDPID")) {
                append(new AMD64ReadProcid(dst));
            } else {
                AMD64ReadTimestampCounterWithProcid procid = new AMD64ReadTimestampCounterWithProcid();
                append(procid);
                emitMove(dst, procid.getProcidResult());
            }
            getArithmetic().emitAnd(dst, emitConstant(LIRKind.value(AMD64Kind.DWORD), JavaConstant.forInt(0xfff)));
        }

        @Override
        public int getArrayLengthOffset() {
            return ConfigurationValues.getObjectLayout().getArrayLengthOffset();
        }

        @Override
        public Register getHeapBaseRegister() {
            return ReservedRegisters.singleton().getHeapBaseRegister();
        }

        @Override
        protected void emitRangeTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue key) {
            super.emitRangeTableSwitch(lowKey, defaultTarget, targets, key);
            markIndirectBranchTargets(targets);
        }

        @Override
        protected void emitHashTableSwitch(JavaConstant[] keys, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue value, Value hash) {
            super.emitHashTableSwitch(keys, defaultTarget, targets, value, hash);
            markIndirectBranchTargets(targets);
        }

        private void markIndirectBranchTargets(LabelRef[] labels) {
            for (LabelRef label : labels) {
                label.getTargetBlock().setIndirectBranchTarget();
            }
        }
    }

    public class SubstrateAMD64NodeLIRBuilder extends AMD64NodeLIRBuilder implements SubstrateNodeLIRBuilder {

        public SubstrateAMD64NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AMD64NodeMatchRules nodeMatchRules) {
            super(graph, gen, nodeMatchRules);
        }

        @Override
        public void doBlockPrologue(@SuppressWarnings("unused") HIRBlock block, @SuppressWarnings("unused") OptionValues options) {
            if (SubstrateOptions.IndirectBranchTargetMarker.getValue() && block.isIndirectBranchTarget()) {
                List<LIRInstruction> lir = gen.getResult().getLIR().getLIRforBlock(block);
                GraalError.guarantee(lir.size() == 1 && lir.get(0) instanceof LabelOp, "block may only contain an initial LabelOp before emitting endbranch");
                gen.append(EndbranchOp.create());
            }
            super.doBlockPrologue(block, options);
        }

        @Override
        public void visitSafepointNode(SafepointNode node) {
            throw shouldNotReachHere("handled by lowering");
        }

        @Override
        public void visitBreakpointNode(BreakpointNode node) {
            JavaType[] sig = new JavaType[node.arguments().size()];
            for (int i = 0; i < sig.length; i++) {
                sig[i] = node.arguments().get(i).stamp(NodeView.DEFAULT).javaType(gen.getMetaAccess());
            }

            CallingConvention convention = gen.getRegisterConfig().getCallingConvention(SubstrateCallingConventionKind.Java.toType(true), null, sig, gen);
            append(new AMD64BreakpointOp(visitInvokeArguments(convention, node.arguments())));
        }

        @Override
        protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
            return new SubstrateDebugInfoBuilder(graph, gen.getProviders().getMetaAccessExtensionProvider(), nodeValueMap);
        }

        @Override
        protected void prologSetParameterNodes(StructuredGraph graph, Value[] params) {
            SubstrateCallingConvention convention = (SubstrateCallingConvention) gen.getResult().getCallingConvention();
            for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                Value inputValue = params[param.index()];
                Value paramValue = gen.emitMove(inputValue);

                /*
                 * In the native ABI, some parameters are not extended to the equivalent Java stack
                 * kinds.
                 */
                if (inputValue.getPlatformKind().getSizeInBytes() < Integer.BYTES) {
                    SubstrateCallingConventionType type = (SubstrateCallingConventionType) convention.getType();
                    assert !type.outgoing && type.nativeABI();
                    JavaKind kind = convention.getArgumentStorageKinds()[param.index()];
                    JavaKind stackKind = kind.getStackKind();
                    if (kind.isUnsigned()) {
                        paramValue = gen.getArithmetic().emitZeroExtend(paramValue, kind.getBitCount(), stackKind.getBitCount());
                    } else {
                        paramValue = gen.getArithmetic().emitSignExtend(paramValue, kind.getBitCount(), stackKind.getBitCount());
                    }
                }

                assert paramValue.getValueKind().equals(gen.getLIRKind(param.stamp(NodeView.DEFAULT)));

                setResult(param, paramValue);
            }
        }

        @Override
        public Value[] visitInvokeArguments(CallingConvention invokeCc, Collection<ValueNode> arguments) {
            Value[] values = super.visitInvokeArguments(invokeCc, arguments);
            SubstrateCallingConventionType type = (SubstrateCallingConventionType) ((SubstrateCallingConvention) invokeCc).getType();

            if (type.usesReturnBuffer()) {
                /*
                 * We save the return buffer so that it can be accessed after the call.
                 */
                assert values.length > 0;
                Value returnBuffer = values[0];
                Variable saved = gen.newVariable(returnBuffer.getValueKind());
                gen.append(gen.getSpillMoveFactory().createMove(saved, returnBuffer));
                values[0] = saved;
            }

            if (type.nativeABI()) {
                VMError.guarantee(values.length == invokeCc.getArgumentCount() - 1, "The last argument should be missing.");
                AllocatableValue raxValue = invokeCc.getArgument(values.length);
                VMError.guarantee(raxValue instanceof RegisterValue && ((RegisterValue) raxValue).getRegister().equals(rax));

                values = Arrays.copyOf(values, values.length + 1);

                // Native functions might have varargs, in which case we need to set %al to the
                // number of XMM registers used for passing arguments
                int xmmCount = 0;
                for (int i = 0; i < values.length - 1; ++i) {
                    Value v = values[i];
                    if (isRegister(v) && asRegister(v).getRegisterCategory().equals(AMD64.XMM)) {
                        xmmCount++;
                    }
                }
                assert xmmCount <= 8;
                gen.emitMoveConstant(raxValue, JavaConstant.forInt(xmmCount));
                values[values.length - 1] = raxValue;
            }

            return values;
        }

        private boolean getDestroysCallerSavedRegisters(ResolvedJavaMethod targetMethod) {
            return ((SubstrateAMD64LIRGenerator) gen).getDestroysCallerSavedRegisters(targetMethod);
        }

        /**
         * For invokes that have an exception handler, the register used for the incoming exception
         * is destroyed at the call site even when registers are caller saved. The normal object
         * return register is used in {@link NodeLIRBuilder#emitReadExceptionObject} also for the
         * exception.
         */
        private Value getExceptionTemp(CallTargetNode callTarget) {
            return ((SubstrateAMD64LIRGenerator) gen).getExceptionTemp(callTarget.invoke() instanceof InvokeWithExceptionNode);
        }

        public BiConsumer<CompilationResultBuilder, Integer> getOffsetRecorder(@SuppressWarnings("unused") IndirectCallTargetNode callTargetNode) {
            return null;
        }

        private static AllocatableValue asReturnedValue(AssignedLocation assignedLocation) {
            assert assignedLocation.assignsToRegister();
            Register.RegisterCategory category = assignedLocation.register().getRegisterCategory();
            LIRKind kind;
            if (category.equals(AMD64.CPU)) {
                kind = LIRKind.value(AMD64Kind.QWORD);
            } else if (category.equals(AMD64.XMM)) {
                kind = LIRKind.value(AMD64Kind.V128_DOUBLE);
            } else {
                throw unsupportedFeature("Register category " + category + " should not be used for returns spanning multiple registers.");
            }
            return assignedLocation.register().asValue(kind);
        }

        @Override
        protected void emitInvoke(LoweredCallTargetNode callTarget, Value[] parameters, LIRFrameState callState, Value result) {
            var cc = (SubstrateCallingConventionType) callTarget.callType();
            verifyCallTarget(callTarget);
            if (callTarget instanceof ComputedIndirectCallTargetNode) {
                assert !cc.customABI();
                emitComputedIndirectCall((ComputedIndirectCallTargetNode) callTarget, result, parameters, AllocatableValue.NONE, callState);
            } else {
                super.emitInvoke(callTarget, parameters, callState, result);
            }

            if (cc.usesReturnBuffer()) {
                /*
                 * The buffer argument was saved in visitInvokeArguments, so that the value was not
                 * killed by the call.
                 */
                Value returnBuffer = parameters[0];
                long offset = 0;
                for (AssignedLocation ret : cc.returnSaving) {
                    Value saveLocation = gen.getArithmetic().emitAdd(returnBuffer, gen.emitJavaConstant(JavaConstant.forLong(offset)), false);
                    AllocatableValue returnedValue = asReturnedValue(ret);
                    gen.getArithmetic().emitStore(returnedValue.getValueKind(), saveLocation, returnedValue, callState, MemoryOrderMode.PLAIN);
                    offset += returnedValue.getPlatformKind().getSizeInBytes();
                }
            }
        }

        @Override
        protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            vzeroupperBeforeCall((SubstrateAMD64LIRGenerator) getLIRGeneratorTool(), parameters, callState, (SharedMethod) targetMethod);
            append(new SubstrateAMD64DirectCallOp(targetMethod, result, parameters, temps, callState,
                            setupJavaFrameAnchor(callTarget), setupJavaFrameAnchorTemp(callTarget), getNewThreadStatus(callTarget),
                            getDestroysCallerSavedRegisters(targetMethod), getExceptionTemp(callTarget)));
        }

        @Override
        protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            // The register allocator cannot handle variables at call sites, need a fixed register.
            Register targetRegister = AMD64.rax;
            if (((SubstrateCallingConventionType) callTarget.callType()).nativeABI()) {
                // Do not use RAX for C calls, it contains the number of XMM registers for varargs.
                targetRegister = AMD64.r10;
            }
            AllocatableValue targetAddress = targetRegister.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRGeneratorTool().getLIRKindTool()));
            gen.emitMove(targetAddress, operand(callTarget.computedAddress()));
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            vzeroupperBeforeCall((SubstrateAMD64LIRGenerator) getLIRGeneratorTool(), parameters, callState, (SharedMethod) targetMethod);

            Value[] multipleResults = new Value[0];
            var cc = (SubstrateCallingConventionType) callTarget.callType();
            if (cc.customABI() && cc.usesReturnBuffer()) {
                multipleResults = Arrays.stream(cc.returnSaving)
                                .map(SubstrateAMD64NodeLIRBuilder::asReturnedValue)
                                .toList().toArray(new Value[0]);
            }

            append(new SubstrateAMD64IndirectCallOp(targetMethod, result, parameters, temps, targetAddress, callState,
                            setupJavaFrameAnchor(callTarget), setupJavaFrameAnchorTemp(callTarget), getNewThreadStatus(callTarget),
                            getDestroysCallerSavedRegisters(targetMethod), getExceptionTemp(callTarget), getOffsetRecorder(callTarget), multipleResults));
        }

        protected void emitComputedIndirectCall(ComputedIndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            assert !((SubstrateCallingConventionType) callTarget.callType()).nativeABI();
            // The register allocator cannot handle variables at call sites, need a fixed register.
            AllocatableValue addressBase = AMD64.rax.asValue(callTarget.getAddressBase().stamp(NodeView.DEFAULT).getLIRKind(getLIRGeneratorTool().getLIRKindTool()));
            gen.emitMove(addressBase, operand(callTarget.getAddressBase()));
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            vzeroupperBeforeCall((SubstrateAMD64LIRGenerator) getLIRGeneratorTool(), parameters, callState, (SharedMethod) targetMethod);
            append(new SubstrateAMD64ComputedIndirectCallOp(targetMethod, result, parameters, temps, addressBase, callTarget.getAddressComputation(), callState,
                            getExceptionTemp(callTarget), gen.getLIRKindTool(), (SharedConstantReflectionProvider) getConstantReflection()));
        }

        private AllocatableValue setupJavaFrameAnchor(CallTargetNode callTarget) {
            if (!hasJavaFrameAnchor(callTarget)) {
                return Value.ILLEGAL;
            }

            /* Register allocator cannot handle variables at call sites, need a fixed register. */
            Register frameAnchorRegister = AMD64.r13;
            AllocatableValue frameAnchor = frameAnchorRegister.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRGeneratorTool().getLIRKindTool()));
            gen.emitMove(frameAnchor, operand(getJavaFrameAnchor(callTarget)));
            return frameAnchor;
        }

        private AllocatableValue setupJavaFrameAnchorTemp(CallTargetNode callTarget) {
            if (!hasJavaFrameAnchor(callTarget)) {
                return Value.ILLEGAL;
            }

            /* Register allocator cannot handle variables at call sites, need a fixed register. */
            return AMD64.r12.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRGeneratorTool().getLIRKindTool()));
        }

        @Override
        public void emitBranch(LogicNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
            if (node instanceof SafepointCheckNode) {
                AMD64SafepointCheckOp op = new AMD64SafepointCheckOp();
                append(op);
                append(new BranchOp(op.getConditionFlag(), trueSuccessor, falseSuccessor, trueSuccessorProbability));
            } else {
                super.emitBranch(node, trueSuccessor, falseSuccessor, trueSuccessorProbability);
            }
        }

        @Override
        public void emitCGlobalDataLoadAddress(CGlobalDataLoadAddressNode node) {
            Variable result = gen.newVariable(gen.getLIRKindTool().getWordKind());
            append(new AMD64CGlobalDataLoadAddressOp(node.getDataInfo(), result));
            setResult(node, result);
        }

        @Override
        public Variable emitReadReturnAddress() {
            assert FrameAccess.returnAddressSize() > 0;
            return getLIRGeneratorTool().emitMove(StackSlot.get(getLIRGeneratorTool().getLIRKind(FrameAccess.getWordStamp()), -FrameAccess.returnAddressSize(), true));
        }

        @Override
        public ForeignCallLinkage lookupGraalStub(ValueNode valueNode, ForeignCallDescriptor foreignCallDescriptor) {
            SharedMethod method = (SharedMethod) valueNode.graph().method();
            if (method != null && method.isForeignCallTarget()) {
                // Emit assembly for snippet stubs
                return null;
            }
            if (AMD64IntrinsicStubs.shouldInlineIntrinsic(valueNode, gen)) {
                // intrinsic can emit specialized code that is small enough to warrant being inlined
                return null;
            }
            // Assume the SVM ForeignCallSignature are identical to the Graal ones.
            return gen.getForeignCalls().lookupForeignCall(chooseCPUFeatureVariant(foreignCallDescriptor, gen.target(), Stubs.getRequiredCPUFeatures(valueNode.getClass())));
        }

    }

    @SuppressWarnings("unlikely-arg-type")
    private static ForeignCallDescriptor chooseCPUFeatureVariant(ForeignCallDescriptor descriptor, TargetDescription target, EnumSet<?> runtimeCheckedCPUFeatures) {
        EnumSet<?> buildtimeCPUFeatures = ImageSingletons.lookup(CPUFeatureAccess.class).buildtimeCPUFeatures();
        EnumSet<?> amd64Features = ((AMD64) target.arch).getFeatures();
        if (buildtimeCPUFeatures.containsAll(runtimeCheckedCPUFeatures) || !amd64Features.containsAll(runtimeCheckedCPUFeatures)) {
            return descriptor;
        } else {
            GraalError.guarantee(RuntimeCompilation.isEnabled(), "should be reached in JIT mode only");
            return new ForeignCallDescriptor(descriptor.getName() + Stubs.RUNTIME_CHECKED_CPU_FEATURES_NAME_SUFFIX, descriptor.getResultType(), descriptor.getArgumentTypes(),
                            descriptor.isReexecutable(), descriptor.getKilledLocations(), descriptor.canDeoptimize(), descriptor.isGuaranteedSafepoint());
        }
    }

    protected static class SubstrateAMD64FrameContext implements FrameContext {

        protected final SharedMethod method;
        protected final CallingConvention callingConvention;

        protected SubstrateAMD64FrameContext(SharedMethod method, CallingConvention callingConvention) {
            this.method = method;
            this.callingConvention = callingConvention;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;

            makeFrame(crb, asm);
            crb.recordMark(PROLOGUE_DECD_RSP);

            if (method.hasCalleeSavedRegisters()) {
                VMError.guarantee(!method.isDeoptTarget(), "Deoptimization runtime cannot fill the callee saved registers");
                AMD64CalleeSavedRegisters.singleton().emitSave((AMD64MacroAssembler) crb.asm, crb.frameMap.totalFrameSize(), crb);
            }
            crb.recordMark(PROLOGUE_END);
        }

        protected void emitEndBranch(CompilationResultBuilder crb) {
            /*
             * Emit an endbranch instruction if we are runtime compiling or the method can be
             * dynamically bound.
             */
            if (SubstrateOptions.IndirectBranchTargetMarker.getValue() && (ImageInfo.inImageRuntimeCode() || !method.canBeStaticallyBound())) {
                ((AMD64Assembler) crb.asm).endbranch();
            }
        }

        protected void makeFrame(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
            emitEndBranch(crb);
            reserveStackFrame(crb, asm);
        }

        protected final void reserveStackFrame(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
            maybePushBasePointer(crb, asm);
            asm.decrementq(rsp, crb.frameMap.frameSize());
        }

        protected void maybePushBasePointer(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
            if (((SubstrateAMD64RegisterConfig) crb.frameMap.getRegisterConfig()).shouldUseBasePointer()) {
                /*
                 * Note that we never use the `enter` instruction so that we have a predictable code
                 * pattern at each method prologue. And `enter` seems to be slower than the explicit
                 * code.
                 */
                asm.push(rbp);
                asm.movq(rbp, rsp);
            }
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
            crb.recordMark(SubstrateMarkId.EPILOGUE_START);

            if (method.hasCalleeSavedRegisters()) {
                JavaKind returnKind = method.getSignature().getReturnKind();
                Register returnRegister = null;
                if (returnKind != JavaKind.Void) {
                    returnRegister = crb.frameMap.getRegisterConfig().getReturnRegister(returnKind);
                }
                AMD64CalleeSavedRegisters.singleton().emitRestore((AMD64MacroAssembler) crb.asm, crb.frameMap.totalFrameSize(), returnRegister, crb);
            }

            if (((SubstrateAMD64RegisterConfig) crb.frameMap.getRegisterConfig()).shouldUseBasePointer()) {
                asm.movq(rsp, rbp);
                asm.pop(rbp);
            } else {
                asm.incrementq(rsp, crb.frameMap.frameSize());
            }

            crb.recordMark(SubstrateMarkId.EPILOGUE_INCD_RSP);
        }

        @Override
        public void returned(CompilationResultBuilder crb) {
            crb.recordMark(SubstrateMarkId.EPILOGUE_END);
        }

    }

    /**
     * Generates the prolog of a {@link com.oracle.svm.core.deopt.Deoptimizer.StubType#EntryStub}
     * method.
     */
    protected static class DeoptEntryStubContext extends SubstrateAMD64FrameContext {
        protected DeoptEntryStubContext(SharedMethod method, CallingConvention callingConvention) {
            super(method, callingConvention);
        }

        @Override
        public void enter(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            RegisterConfig registerConfig = tasm.frameMap.getRegisterConfig();
            Register gpReturnReg = registerConfig.getReturnRegister(JavaKind.Long);
            Register fpReturnReg = registerConfig.getReturnRegister(JavaKind.Double);

            /* Move the DeoptimizedFrame into the first calling convention register. */
            Register deoptimizedFrame = ValueUtil.asRegister(callingConvention.getArgument(0));
            assert !deoptimizedFrame.equals(gpReturnReg) : "overwriting return reg";
            asm.movq(deoptimizedFrame, registerConfig.getFrameRegister());

            /* Copy the original return registers values into the argument registers. */
            asm.movq(ValueUtil.asRegister(callingConvention.getArgument(1)), gpReturnReg);
            asm.movdq(ValueUtil.asRegister(callingConvention.getArgument(2)), fpReturnReg);

            /* Add a dummy return address to the stack so that RSP is properly aligned. */
            asm.subq(registerConfig.getFrameRegister(), FrameAccess.returnAddressSize());
            super.enter(tasm);
        }
    }

    /**
     * Generates the epilog of a {@link com.oracle.svm.core.deopt.Deoptimizer.StubType#ExitStub}
     * method.
     */
    protected static class DeoptExitStubContext extends SubstrateAMD64FrameContext {
        protected DeoptExitStubContext(SharedMethod method, CallingConvention callingConvention) {
            super(method, callingConvention);
        }

        @Override
        public void enter(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;

            /* The new stack pointer is passed in as the first method parameter. */
            Register firstParameter = ValueUtil.asRegister(callingConvention.getArgument(0));
            asm.movq(rsp, firstParameter);
            /*
             * Compensate that we set the stack pointer after the return address was pushed. Note
             * that the "new" frame location does not have a valid return address at this point.
             * That is OK because the return address for the deoptimization target frame will be
             * patched into this location.
             *
             * We make space for at least 2 return address sizes. This ensures the stack is 16 byte
             * aligned. If the compiler uses a relative base pointer (rbp register) to remember the
             * old stack pointer, we use the additional space to store this value.
             */
            asm.subq(rsp, 2 * FrameAccess.returnAddressSize());

            /*
             * Save the floating point return value (which is the third argument to the function)
             * onto the stack so that it can be restored on leave. We don't actually require the gp
             * return value to be pushed on the stack, but we must ensure the stack is 16 byte
             * aligned, so we push it anyway.
             */
            asm.push(ValueUtil.asRegister(callingConvention.getArgument(1)));
            asm.push(ValueUtil.asRegister(callingConvention.getArgument(2)));

            super.enter(tasm);
        }

        @Override
        public void leave(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            RegisterConfig registerConfig = tasm.frameMap.getRegisterConfig();
            Register gpReturnReg = registerConfig.getReturnRegister(JavaKind.Long);
            Register fpReturnReg = registerConfig.getReturnRegister(JavaKind.Double);

            super.leave(tasm);

            /*
             * Restore the floating point return value from the stack into the floating point return
             * register. The general purpose register is returned by the rewriteStackStub function.
             */
            asm.movq(fpReturnReg, new AMD64Address(rsp, 0));
            asm.addq(rsp, 8);
            asm.pop(gpReturnReg);

            /*
             * If the compiler uses a relative base pointer, we restore it again here. Otherwise we
             * need to skip over it to restore the stack.
             */
            if (((SubstrateAMD64RegisterConfig) tasm.frameMap.getRegisterConfig()).shouldUseBasePointer()) {
                asm.pop(rbp);
            } else {
                asm.addq(rsp, 8);
            }
        }
    }

    static class SubstrateReferenceMapBuilderFactory implements FrameMap.ReferenceMapBuilderFactory {
        @Override
        public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
            return new SubstrateReferenceMapBuilder(totalFrameSize);
        }
    }

    protected static class SubstrateAMD64MoveFactory extends AMD64MoveFactory {

        private final SharedMethod method;
        protected final LIRKindTool lirKindTool;

        protected SubstrateAMD64MoveFactory(BackupSlotProvider backupSlotProvider, SharedMethod method, LIRKindTool lirKindTool) {
            super(backupSlotProvider);
            this.method = method;
            this.lirKindTool = lirKindTool;
        }

        @Override
        public boolean allowConstantToStackMove(Constant constant) {
            if (constant instanceof SubstrateObjectConstant && method.isDeoptTarget()) {
                return false;
            }
            return super.allowConstantToStackMove(constant);
        }

        private static JavaConstant getZeroConstant(AllocatableValue dst) {
            int size = dst.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            switch (size) {
                case 32:
                    return JavaConstant.INT_0;
                case 64:
                    return JavaConstant.LONG_0;
                default:
                    throw VMError.shouldNotReachHereUnexpectedInput(size); // ExcludeFromJacocoGeneratedReport
            }
        }

        @Override
        public AMD64LIRInstruction createLoad(AllocatableValue dst, Constant src) {
            if (CompressedNullConstant.COMPRESSED_NULL.equals(src)) {
                return super.createLoad(dst, getZeroConstant(dst));
            } else if (src instanceof CompressibleConstant) {
                return loadObjectConstant(dst, (CompressibleConstant) src);
            } else if (src instanceof SubstrateMethodPointerConstant) {
                return new AMD64LoadMethodPointerConstantOp(dst, (SubstrateMethodPointerConstant) src);
            }
            return super.createLoad(dst, src);
        }

        @Override
        public LIRInstruction createStackLoad(AllocatableValue dst, Constant src) {
            if (CompressedNullConstant.COMPRESSED_NULL.equals(src)) {
                return super.createStackLoad(dst, getZeroConstant(dst));
            } else if (src instanceof CompressibleConstant) {
                return loadObjectConstant(dst, (SubstrateObjectConstant) src);
            } else if (src instanceof SubstrateMethodPointerConstant) {
                return new AMD64LoadMethodPointerConstantOp(dst, (SubstrateMethodPointerConstant) src);
            }
            return super.createStackLoad(dst, src);
        }

        protected AMD64LIRInstruction loadObjectConstant(AllocatableValue dst, CompressibleConstant constant) {
            if (ReferenceAccess.singleton().haveCompressedReferences()) {
                RegisterValue heapBase = ReservedRegisters.singleton().getHeapBaseRegister().asValue();
                return new LoadCompressedObjectConstantOp(dst, constant, heapBase, getCompressEncoding(), lirKindTool);
            }
            return new MoveFromConstOp(dst, constant);
        }

        /*
         * The constant denotes the result produced by this node. Thus if the constant is
         * compressed, the result must be compressed and vice versa. Both compressed and
         * uncompressed constants can be loaded by compiled code.
         *
         * Method getConstant() could uncompress the constant value from the node input. That would
         * require a few indirections and an allocation of an uncompressed constant. The allocation
         * could be eliminated if we stored uncompressed ConstantValue as input. But as this method
         * looks performance-critical, it is still faster to memorize the original constant in the
         * node.
         */
        public static final class LoadCompressedObjectConstantOp extends PointerCompressionOp implements LoadConstantOp {
            public static final LIRInstructionClass<LoadCompressedObjectConstantOp> TYPE = LIRInstructionClass.create(LoadCompressedObjectConstantOp.class);
            private final CompressibleConstant constant;

            static Constant asCompressed(CompressibleConstant constant) {
                // We only want compressed references in code
                return constant.isCompressed() ? constant : constant.compress();
            }

            LoadCompressedObjectConstantOp(AllocatableValue result, CompressibleConstant constant, AllocatableValue baseRegister, CompressEncoding encoding, LIRKindTool lirKindTool) {
                super(TYPE, result, new ConstantValue(lirKindTool.getNarrowOopKind(), asCompressed(constant)), baseRegister, encoding, true, lirKindTool);
                this.constant = constant;
            }

            @Override
            public Constant getConstant() {
                return constant;
            }

            @Override
            public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
                /*
                 * WARNING: must NOT have side effects. Preserve the flags register!
                 */
                Register resultReg = getResultRegister();
                int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
                Constant inputConstant = asConstantValue(getInput()).getConstant();
                if (masm.inlineObjects()) {
                    crb.recordInlineDataInCode(inputConstant);
                    if (referenceSize == 4) {
                        masm.movl(resultReg, 0xDEADDEAD, true);
                    } else {
                        masm.movq(resultReg, 0xDEADDEADDEADDEADL, true);
                    }
                } else {
                    AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(inputConstant, referenceSize);
                    if (referenceSize == 4) {
                        masm.movl(resultReg, address);
                    } else {
                        masm.movq(resultReg, address);
                    }
                }
                if (!constant.isCompressed()) { // the result is expected to be uncompressed
                    Register baseReg = getBaseRegister();
                    boolean preserveFlagsRegister = true;
                    emitUncompressWithBaseRegister(masm, resultReg, baseReg, getShift(), preserveFlagsRegister);
                }
            }
        }
    }

    private FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        FrameMap frameMap = new AMD64FrameMap(getProviders().getCodeCache(), registerConfigNonNull, new SubstrateReferenceMapBuilderFactory(),
                        ((SubstrateAMD64RegisterConfig) registerConfigNonNull).shouldUseBasePointer());
        return new AMD64FrameMapBuilder(frameMap, getCodeCache(), registerConfigNonNull);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, RegisterAllocationConfig registerAllocationConfig, StructuredGraph graph, Object stub) {
        SharedMethod method = (SharedMethod) graph.method();
        SubstrateCallingConventionKind ccKind = method.getCallingConventionKind();
        SubstrateCallingConventionType ccType = ccKind.isCustom() ? method.getCustomCallingConventionType() : ccKind.toType(false);
        CallingConvention callingConvention = CodeUtil.getCallingConvention(getCodeCache(), ccType, method, this);
        return new SubstrateLIRGenerationResult(compilationId, lir, newFrameMapBuilder(registerAllocationConfig.getRegisterConfig()), callingConvention, registerAllocationConfig, method);
    }

    protected AMD64ArithmeticLIRGenerator createArithmeticLIRGen(RegisterValue nullRegisterValue) {
        return new AMD64ArithmeticLIRGenerator(nullRegisterValue);
    }

    protected AMD64MoveFactoryBase createMoveFactory(LIRGenerationResult lirGenRes, BackupSlotProvider backupSlotProvider) {
        SharedMethod method = ((SubstrateLIRGenerationResult) lirGenRes).getMethod();
        return new SubstrateAMD64MoveFactory(backupSlotProvider, method, createLirKindTool());
    }

    protected static class SubstrateAMD64LIRKindTool extends AMD64LIRKindTool {
        @Override
        public LIRKind getNarrowOopKind() {
            return LIRKind.compressedReference(AMD64Kind.QWORD);
        }

        @Override
        public LIRKind getNarrowPointerKind() {
            throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
        }
    }

    protected LIRKindTool createLirKindTool() {
        return new SubstrateAMD64LIRKindTool();
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        RegisterValue nullRegisterValue = useLinearPointerCompression() ? ReservedRegisters.singleton().getHeapBaseRegister().asValue() : null;
        AMD64ArithmeticLIRGenerator arithmeticLIRGen = createArithmeticLIRGen(nullRegisterValue);
        BackupSlotProvider backupSlotProvider = new BackupSlotProvider(lirGenRes.getFrameMapBuilder());
        AMD64MoveFactoryBase moveFactory = createMoveFactory(lirGenRes, backupSlotProvider);
        return new SubstrateAMD64LIRGenerator(createLirKindTool(), arithmeticLIRGen, moveFactory, getProviders(), lirGenRes);
    }

    protected AMD64NodeMatchRules createMatchRules(LIRGeneratorTool lirGen) {
        return new AMD64NodeMatchRules(lirGen);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        AMD64NodeMatchRules nodeMatchRules = createMatchRules(lirGen);
        return new SubstrateAMD64NodeLIRBuilder(graph, lirGen, nodeMatchRules);
    }

    protected static boolean useLinearPointerCompression() {
        return SubstrateOptions.SpawnIsolates.getValue();
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenResult, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        LIR lir = lirGenResult.getLIR();
        OptionValues options = lir.getOptions();
        AMD64MacroAssembler masm = new AMD64MacroAssembler(getTarget(), options, true);
        PatchConsumerFactory patchConsumerFactory;
        if (SubstrateUtil.HOSTED) {
            patchConsumerFactory = PatchConsumerFactory.HostedPatchConsumerFactory.factory();
        } else {
            patchConsumerFactory = PatchConsumerFactory.NativePatchConsumerFactory.factory();
        }
        masm.setCodePatchingAnnotationConsumer(patchConsumerFactory.newConsumer(compilationResult));
        SharedMethod method = ((SubstrateLIRGenerationResult) lirGenResult).getMethod();
        Deoptimizer.StubType stubType = method.getDeoptStubType();
        DataBuilder dataBuilder = new SubstrateDataBuilder();
        CallingConvention callingConvention = lirGenResult.getCallingConvention();
        final FrameContext frameContext;
        if (stubType == Deoptimizer.StubType.EntryStub) {
            frameContext = new DeoptEntryStubContext(method, callingConvention);
        } else if (stubType == Deoptimizer.StubType.ExitStub) {
            frameContext = new DeoptExitStubContext(method, callingConvention);
        } else {
            frameContext = createFrameContext(method, callingConvention);
        }
        DebugContext debug = lir.getDebug();
        Register uncompressedNullRegister = useLinearPointerCompression() ? ReservedRegisters.singleton().getHeapBaseRegister() : Register.None;
        CompilationResultBuilder tasm = factory.createBuilder(getProviders(), lirGenResult.getFrameMap(), masm, dataBuilder, frameContext, options, debug, compilationResult,
                        uncompressedNullRegister, lir);
        tasm.setTotalFrameSize(lirGenResult.getFrameMap().totalFrameSize());
        return tasm;
    }

    protected FrameContext createFrameContext(SharedMethod method, CallingConvention callingConvention) {
        return new SubstrateAMD64FrameContext(method, callingConvention);
    }

    @Override
    public BasePhase<CoreProviders> newAddressLoweringPhase(CodeCacheProvider codeCache) {
        CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);
        return new AddressLoweringByNodePhase(new SubstrateAMD64AddressLowering(compressEncoding));
    }

    @Override
    public CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        return new SubstrateCompiledCode(compilationResult);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, ResolvedJavaMethod installedCodeOwner, EntryPointDecorator entryPointDecorator) {
        crb.emitLIR();
    }

    @Override
    public CompilationResult createJNITrampolineMethod(ResolvedJavaMethod method, CompilationIdentifier identifier,
                    RegisterValue threadArg, int threadIsolateOffset, RegisterValue methodIdArg, int methodObjEntryPointOffset) {

        CompilationResult result = new CompilationResult(identifier);
        AMD64Assembler asm = new AMD64Assembler(getTarget());
        if (SubstrateOptions.SpawnIsolates.getValue()) { // method id is offset from heap base
            asm.movq(rax, new AMD64Address(threadArg.getRegister(), threadIsolateOffset));
            /*
             * Load the isolate pointer from the JNIEnv argument (same as the isolate thread). The
             * isolate pointer is equivalent to the heap base address (which would normally be
             * provided via Isolate.getHeapBase which is a no-op), which we then use to access the
             * method object and read the entry point.
             */
            asm.addq(rax, methodIdArg.getRegister()); // address of JNIAccessibleMethod
            asm.jmp(new AMD64Address(rax, methodObjEntryPointOffset));
        } else { // methodId is absolute address
            asm.jmp(new AMD64Address(methodIdArg.getRegister(), methodObjEntryPointOffset));
        }
        result.recordMark(asm.position(), PROLOGUE_DECD_RSP);
        result.recordMark(asm.position(), PROLOGUE_END);
        byte[] instructions = asm.close(true);
        result.setTargetCode(instructions, instructions.length);
        result.setTotalFrameSize(getTarget().wordSize); // not really, but 0 not allowed
        return result;
    }
}
