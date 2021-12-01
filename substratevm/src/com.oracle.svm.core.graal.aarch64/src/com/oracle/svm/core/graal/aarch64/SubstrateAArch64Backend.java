/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.aarch64;

import static com.oracle.svm.core.graal.aarch64.SubstrateAArch64RegisterConfig.fp;
import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_DECD_RSP;
import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_END;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;
import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.core.common.GraalOptions.ZapStackOnMethodEntry;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstantValue;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.BranchTargetOutOfBoundsException;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.PrefetchMode;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.aarch64.AArch64AddressLoweringByUse;
import org.graalvm.compiler.core.aarch64.AArch64ArithmeticLIRGenerator;
import org.graalvm.compiler.core.aarch64.AArch64LIRGenerator;
import org.graalvm.compiler.core.aarch64.AArch64LIRKindTool;
import org.graalvm.compiler.core.aarch64.AArch64MoveFactory;
import org.graalvm.compiler.core.aarch64.AArch64NodeLIRBuilder;
import org.graalvm.compiler.core.aarch64.AArch64NodeMatchRules;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.gen.DebugInfoBuilder;
import org.graalvm.compiler.core.gen.LIRGenerationProvider;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.lir.aarch64.AArch64BreakpointOp;
import org.graalvm.compiler.lir.aarch64.AArch64Call;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMap;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMapBuilder;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64Move;
import org.graalvm.compiler.lir.aarch64.AArch64Move.PointerCompressionOp;
import org.graalvm.compiler.lir.aarch64.AArch64PrefetchOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.framemap.FrameMapBuilderTool;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.gen.MoveFactory;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.NodeValueMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.AddressLoweringByUsePhase;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.code.PatchConsumerFactory;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCompiledCode;
import com.oracle.svm.core.graal.code.SubstrateDataBuilder;
import com.oracle.svm.core.graal.code.SubstrateDebugInfoBuilder;
import com.oracle.svm.core.graal.code.SubstrateLIRGenerator;
import com.oracle.svm.core.graal.code.SubstrateNodeLIRBuilder;
import com.oracle.svm.core.graal.lir.VerificationMarkerOp;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.SubstrateReferenceMapBuilder;
import com.oracle.svm.core.meta.CompressedNullConstant;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class SubstrateAArch64Backend extends SubstrateBackend implements LIRGenerationProvider {

    protected static CompressEncoding getCompressEncoding() {
        return ImageSingletons.lookup(CompressEncoding.class);
    }

    public SubstrateAArch64Backend(Providers providers) {
        super(providers);
    }

    @Opcode("CALL_DIRECT")
    public static class SubstrateAArch64DirectCallOp extends AArch64Call.DirectCallOp {
        public static final LIRInstructionClass<SubstrateAArch64DirectCallOp> TYPE = LIRInstructionClass.create(SubstrateAArch64DirectCallOp.class);

        private final RuntimeConfiguration runtimeConfiguration;
        private final int newThreadStatus;
        @Use({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchor;

        private final boolean destroysCallerSavedRegisters;
        @Temp({REG, OperandFlag.ILLEGAL}) private Value exceptionTemp;
        /*
         * Make it explicit that this operation overrides the link register. This is important to
         * know when the callTarget uses the StubCallingConvention.
         */
        @Temp({REG}) private Value linkReg;

        public SubstrateAArch64DirectCallOp(RuntimeConfiguration runtimeConfiguration, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state,
                        Value javaFrameAnchor, int newThreadStatus, boolean destroysCallerSavedRegisters, Value exceptionTemp) {
            super(TYPE, callTarget, result, parameters, temps, state);
            this.runtimeConfiguration = runtimeConfiguration;
            this.javaFrameAnchor = javaFrameAnchor;
            this.newThreadStatus = newThreadStatus;
            this.destroysCallerSavedRegisters = destroysCallerSavedRegisters;
            this.exceptionTemp = exceptionTemp;
            this.linkReg = lr.asValue(LIRKind.value(AArch64Kind.QWORD));
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            maybeTransitionToNative(crb, masm, runtimeConfiguration, javaFrameAnchor, state, newThreadStatus);
            AArch64Call.directCall(crb, masm, callTarget, null, state);
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return destroysCallerSavedRegisters;
        }
    }

    @Opcode("CALL_INDIRECT")
    public static class SubstrateAArch64IndirectCallOp extends AArch64Call.IndirectCallOp {
        public static final LIRInstructionClass<SubstrateAArch64IndirectCallOp> TYPE = LIRInstructionClass.create(SubstrateAArch64IndirectCallOp.class);
        private final RuntimeConfiguration runtimeConfiguration;
        private final int newThreadStatus;
        @Use({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchor;

        private final boolean destroysCallerSavedRegisters;
        @Temp({REG, OperandFlag.ILLEGAL}) private Value exceptionTemp;
        /*
         * Make it explicit that this operation overrides the link register. This is important to
         * know when the callTarget uses the StubCallingConvention.
         */
        @Temp({REG}) private Value linkReg;

        public SubstrateAArch64IndirectCallOp(RuntimeConfiguration runtimeConfiguration, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress,
                        LIRFrameState state, Value javaFrameAnchor, int newThreadStatus, boolean destroysCallerSavedRegisters, Value exceptionTemp) {
            super(TYPE, callTarget, result, parameters, temps, targetAddress, state);
            this.runtimeConfiguration = runtimeConfiguration;
            this.javaFrameAnchor = javaFrameAnchor;
            this.newThreadStatus = newThreadStatus;
            this.destroysCallerSavedRegisters = destroysCallerSavedRegisters;
            this.exceptionTemp = exceptionTemp;
            this.linkReg = lr.asValue(LIRKind.value(AArch64Kind.QWORD));
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            maybeTransitionToNative(crb, masm, runtimeConfiguration, javaFrameAnchor, state, newThreadStatus);
            super.emitCode(crb, masm);
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return destroysCallerSavedRegisters;
        }
    }

    static void maybeTransitionToNative(CompilationResultBuilder crb, AArch64MacroAssembler masm, RuntimeConfiguration runtimeConfiguration, Value javaFrameAnchor, LIRFrameState state,
                    int newThreadStatus) {
        if (ValueUtil.isIllegal(javaFrameAnchor)) {
            /* Not a call that needs to set up a JavaFrameAnchor. */
            assert newThreadStatus == StatusSupport.STATUS_ILLEGAL;
            return;
        }
        assert StatusSupport.isValidStatus(newThreadStatus);

        Register anchor = asRegister(javaFrameAnchor);

        /*
         * We record the instruction to load the current instruction pointer as a Call infopoint, so
         * that the same metadata is emitted in the machine code as for a normal call instruction.
         * The adr loads the offset 8 relative to the begin of the adr instruction, which is the
         * same as for a blr instruction. So the usual AArch64 specific semantics that all the
         * metadata is registered for the end of the instruction just works.
         */
        int startPos = masm.position();
        try (ScratchRegister scratch = masm.getScratchRegister()) {
            Register tempRegister = scratch.getRegister();
            // Save PC
            masm.adr(tempRegister, 4); // Read PC + 4
            crb.recordIndirectCall(startPos, masm.position(), null, state);
            masm.str(64, tempRegister,
                            AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, anchor, runtimeConfiguration.getJavaFrameAnchorLastIPOffset()));
            // Save SP
            masm.mov(64, tempRegister, sp);
            masm.str(64, tempRegister,
                            AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, anchor, runtimeConfiguration.getJavaFrameAnchorLastSPOffset()));
        }

        if (SubstrateOptions.MultiThreaded.getValue()) {
            /*
             * Change VMThread status from Java to Native. Note a "store release" is needed for this
             * update to ensure VMThread status is only updated once all prior stores are also
             * observable.
             */
            try (ScratchRegister scratch1 = masm.getScratchRegister(); ScratchRegister scratch2 = masm.getScratchRegister()) {
                Register statusValueRegister = scratch1.getRegister();
                Register statusAddressRegister = scratch2.getRegister();
                masm.mov(statusValueRegister, newThreadStatus);
                masm.loadAlignedAddress(32, statusAddressRegister, ReservedRegisters.singleton().getThreadRegister(), runtimeConfiguration.getVMThreadStatusOffset());
                masm.stlr(32, statusValueRegister, statusAddressRegister);
            }
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
                ((AArch64MacroAssembler) crb.asm).brk(AArch64MacroAssembler.AArch64ExceptionCode.BREAKPOINT);
            }
        }
    }

    protected static final class SubstrateLIRGenerationResult extends LIRGenerationResult {

        private final SharedMethod method;

        public SubstrateLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig,
                        CallingConvention callingConvention, SharedMethod method) {
            super(compilationId, lir, frameMapBuilder, registerAllocationConfig, callingConvention);
            this.method = method;

            if (method.hasCalleeSavedRegisters()) {
                AArch64CalleeSavedRegisters calleeSavedRegisters = AArch64CalleeSavedRegisters.singleton();
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

    protected class SubstrateAArch64LIRGenerator extends AArch64LIRGenerator implements SubstrateLIRGenerator {

        public SubstrateAArch64LIRGenerator(LIRKindTool lirKindTool, AArch64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, Providers providers, LIRGenerationResult lirGenRes) {
            super(lirKindTool, arithmeticLIRGen, moveFactory, providers, lirGenRes);
        }

        @Override
        public SubstrateLIRGenerationResult getResult() {
            return (SubstrateLIRGenerationResult) super.getResult();
        }

        @Override
        public SubstrateRegisterConfig getRegisterConfig() {
            return (SubstrateRegisterConfig) super.getRegisterConfig();
        }

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

            LIRKind wordKind = getLIRKindTool().getWordKind();
            Value codeOffsetInImage = emitConstant(wordKind, JavaConstant.forLong(targetMethod.getCodeOffsetInImage()));
            Value codeInfo = emitJavaConstant(SubstrateObjectConstant.forObject(CodeInfoTable.getImageCodeCache()));
            int size = wordKind.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int codeStartFieldOffset = getRuntimeConfiguration().getImageCodeInfoCodeStartOffset();
            Value codeStartField = AArch64AddressValue.makeAddress(wordKind, size, asAllocatable(codeInfo), codeStartFieldOffset);
            Value codeStart = getArithmetic().emitLoad(wordKind, codeStartField, null);
            return getArithmetic().emitAdd(codeStart, codeOffsetInImage, false);
        }

        @Override
        protected void emitForeignCallOp(ForeignCallLinkage linkage, Value targetAddress, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
            SubstrateForeignCallLinkage callTarget = (SubstrateForeignCallLinkage) linkage;
            SharedMethod targetMethod = (SharedMethod) callTarget.getMethod();
            if (shouldEmitOnlyIndirectCalls()) {
                RegisterValue targetRegister = AArch64.lr.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRKindTool()));
                emitMove(targetRegister, targetAddress);
                append(new SubstrateAArch64IndirectCallOp(getRuntimeConfiguration(), targetMethod, result, arguments, temps, targetRegister, info, Value.ILLEGAL, StatusSupport.STATUS_ILLEGAL,
                                getDestroysCallerSavedRegisters(targetMethod), Value.ILLEGAL));
            } else {
                assert targetAddress == null;
                append(new SubstrateAArch64DirectCallOp(getRuntimeConfiguration(), targetMethod, result, arguments, temps, info, Value.ILLEGAL, StatusSupport.STATUS_ILLEGAL,
                                getDestroysCallerSavedRegisters(targetMethod), Value.ILLEGAL));
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
            append(new AArch64InstructionSynchronizationBarrierOp());
        }

        @Override
        public void emitFarReturn(AllocatableValue result, Value stackPointer, Value ip, boolean fromMethodWithCalleeSavedRegisters) {
            append(new AArch64FarReturnOp(asAllocatable(result), asAllocatable(stackPointer), asAllocatable(ip), fromMethodWithCalleeSavedRegisters));
        }

        @Override
        public void emitDeadEnd() {
            append(new DeadEndOp());
        }

        @Override
        public void emitPrefetchAllocate(Value address) {
            append(new AArch64PrefetchOp(asAddressValue(address, AArch64Address.ANY_SIZE), PrefetchMode.PSTL1KEEP));
        }

        @Override
        public Value emitCompress(Value pointer, CompressEncoding encoding, boolean isNonNull) {
            Variable result = newVariable(getLIRKindTool().getNarrowOopKind());
            boolean nonNull = useLinearPointerCompression() || isNonNull;
            append(new AArch64Move.CompressPointerOp(result, asAllocatable(pointer), ReservedRegisters.singleton().getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        }

        @Override
        public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean isNonNull) {
            assert pointer.getValueKind(LIRKind.class).getPlatformKind() == getLIRKindTool().getNarrowOopKind().getPlatformKind();
            Variable result = newVariable(getLIRKindTool().getObjectKind());
            boolean nonNull = useLinearPointerCompression() || isNonNull;
            append(new AArch64Move.UncompressPointerOp(result, asAllocatable(pointer), ReservedRegisters.singleton().getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        }

        @Override
        public void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args) {
            throw unimplemented();
        }

        @Override
        public void emitConvertNullToZero(AllocatableValue result, Value value) {
            if (useLinearPointerCompression()) {
                append(new AArch64Move.ConvertNullToZeroOp(result, (AllocatableValue) value));
            } else {
                emitMove(result, value);
            }
        }

        @Override
        public void emitConvertZeroToNull(AllocatableValue result, Value value) {
            if (useLinearPointerCompression()) {
                append(new AArch64Move.ConvertZeroToNullOp(result, (AllocatableValue) value));
            } else {
                emitMove(result, value);
            }
        }

        @Override
        public void emitReturn(JavaKind kind, Value input) {
            AllocatableValue operand = Value.ILLEGAL;
            if (input != null) {
                operand = resultOperandFor(kind, input.getValueKind());
                emitMove(operand, input);
            }
            append(new AArch64ControlFlow.ReturnOp(operand));
        }
    }

    public final class SubstrateAArch64NodeLIRBuilder extends AArch64NodeLIRBuilder implements SubstrateNodeLIRBuilder {

        public SubstrateAArch64NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AArch64NodeMatchRules nodeMatchRules) {
            super(graph, gen, nodeMatchRules);
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
            append(new AArch64BreakpointOp(visitInvokeArguments(convention, node.arguments())));
        }

        @Override
        protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
            return new SubstrateDebugInfoBuilder(graph, gen.getProviders().getMetaAccessExtensionProvider(), nodeValueMap);
        }

        private boolean getDestroysCallerSavedRegisters(ResolvedJavaMethod targetMethod) {
            return ((SubstrateAArch64LIRGenerator) gen).getDestroysCallerSavedRegisters(targetMethod);
        }

        /**
         * For invokes that have an exception handler, the register used for the incoming exception
         * is destroyed at the call site even when registers are caller saved. The normal object
         * return register is used in {@link NodeLIRBuilder#emitReadExceptionObject} also for the
         * exception.
         */
        private Value getExceptionTemp(CallTargetNode callTarget) {
            if (callTarget.invoke() instanceof InvokeWithExceptionNode) {
                return gen.getRegisterConfig().getReturnRegister(JavaKind.Object).asValue();
            } else {
                return Value.ILLEGAL;
            }
        }

        @Override
        protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            append(new SubstrateAArch64DirectCallOp(getRuntimeConfiguration(), targetMethod, result, parameters, temps, callState, setupJavaFrameAnchor(callTarget),
                            getNewThreadStatus(callTarget), getDestroysCallerSavedRegisters(targetMethod), getExceptionTemp(callTarget)));
        }

        @Override
        protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            // The register allocator cannot handle variables at call sites, need a fixed register.
            Register targetRegister = AArch64.lr;
            AllocatableValue targetAddress = targetRegister.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRGeneratorTool().getLIRKindTool()));
            gen.emitMove(targetAddress, operand(callTarget.computedAddress()));
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            append(new SubstrateAArch64IndirectCallOp(getRuntimeConfiguration(), targetMethod, result, parameters, temps, targetAddress, callState, setupJavaFrameAnchor(callTarget),
                            getNewThreadStatus(callTarget), getDestroysCallerSavedRegisters(targetMethod), getExceptionTemp(callTarget)));
        }

        private AllocatableValue setupJavaFrameAnchor(CallTargetNode callTarget) {
            if (!hasJavaFrameAnchor(callTarget)) {
                return Value.ILLEGAL;
            }
            /* Register allocator cannot handle variables at call sites, need a fixed register. */
            Register frameAnchorRegister = AArch64.r13;
            AllocatableValue frameAnchor = frameAnchorRegister.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRGeneratorTool().getLIRKindTool()));
            gen.emitMove(frameAnchor, operand(getJavaFrameAnchor(callTarget)));
            return frameAnchor;
        }

        @Override
        public void emitBranch(LogicNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
            if (node instanceof SafepointCheckNode) {
                AArch64SafepointCheckOp op = new AArch64SafepointCheckOp();
                append(op);
                append(new AArch64ControlFlow.BranchOp(op.getConditionFlag(), trueSuccessor, falseSuccessor, trueSuccessorProbability));
            } else {
                super.emitBranch(node, trueSuccessor, falseSuccessor, trueSuccessorProbability);
            }
        }

        @Override
        public void emitCGlobalDataLoadAddress(CGlobalDataLoadAddressNode node) {
            Variable result = gen.newVariable(gen.getLIRKindTool().getWordKind());
            append(new AArch64CGlobalDataLoadAddressOp(node.getDataInfo(), result));
            setResult(node, result);
        }

        @Override
        public Variable emitReadReturnAddress() {
            return getLIRGeneratorTool().emitMove(StackSlot.get(getLIRGeneratorTool().getLIRKind(FrameAccess.getWordStamp()), -FrameAccess.returnAddressSize(), true));
        }
    }

    protected static class SubstrateAArch64FrameContext implements FrameContext {

        protected final SharedMethod method;

        protected SubstrateAArch64FrameContext(SharedMethod method) {
            this.method = method;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            FrameMap frameMap = crb.frameMap;
            final int frameSize = frameMap.frameSize();
            final int totalFrameSize = frameMap.totalFrameSize();
            assert frameSize + 2 * crb.target.arch.getWordSize() == totalFrameSize : "totalFramesize should be frameSize + 2 words";
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;

            crb.blockComment("[method prologue]");
            makeFrame(crb, masm, totalFrameSize, frameSize);
            crb.recordMark(PROLOGUE_DECD_RSP);

            if (ZapStackOnMethodEntry.getValue(crb.getOptions())) {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register scratch = sc.getRegister();
                    int longSize = Long.BYTES;
                    masm.mov(64, scratch, sp);
                    AArch64Address address = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, scratch, longSize);
                    try (ScratchRegister sc2 = masm.getScratchRegister()) {
                        Register value = sc2.getRegister();
                        masm.mov(value, 0xBADDECAFFC0FFEEL);
                        for (int i = 0; i < frameSize; i += longSize) {
                            masm.str(64, value, address);
                        }
                    }

                }
            }

            if (method.hasCalleeSavedRegisters()) {
                VMError.guarantee(!method.isDeoptTarget(), "Deoptimization runtime cannot fill the callee saved registers");
                AArch64CalleeSavedRegisters.singleton().emitSave(masm, totalFrameSize);
            }
            crb.recordMark(PROLOGUE_END);
        }

        protected void makeFrame(CompilationResultBuilder crb, AArch64MacroAssembler masm, int totalFrameSize, int frameSize) {
            boolean preserveFramePointer = ((SubstrateAArch64RegisterConfig) crb.frameMap.getRegisterConfig()).shouldPreserveFramePointer();
            // based on HotSpot's macroAssembler_aarch64.cpp MacroAssembler::build_frame
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                assert totalFrameSize > 0;
                AArch64Address.AddressingMode addressingMode = AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
                if (AArch64Address.isValidImmediateAddress(64, addressingMode, frameSize)) {
                    masm.sub(64, sp, sp, totalFrameSize);
                    masm.stp(64, fp, lr, AArch64Address.createImmediateAddress(64, addressingMode, sp, frameSize));
                    if (preserveFramePointer) {
                        masm.add(64, fp, sp, frameSize);
                    }
                } else {
                    // frameRecordSize = 2 * wordSize (space for fp & lr)
                    int frameRecordSize = totalFrameSize - frameSize;
                    masm.stp(64, fp, lr, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_PAIR_PRE_INDEXED, sp, -frameRecordSize));
                    if (preserveFramePointer) {
                        masm.mov(64, fp, sp);
                    }
                    masm.sub(64, sp, sp, frameSize, scratch);
                }
            }
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            FrameMap frameMap = crb.frameMap;
            final int frameSize = frameMap.frameSize();
            final int totalFrameSize = frameMap.totalFrameSize();
            assert frameSize + 2 * crb.target.arch.getWordSize() == totalFrameSize : "totalFramesize should be frameSize + 2 words";
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;

            crb.blockComment("[method epilogue]");
            crb.recordMark(SubstrateMarkId.EPILOGUE_START);
            if (method.hasCalleeSavedRegisters()) {
                JavaKind returnKind = method.getSignature().getReturnKind();
                Register returnRegister = null;
                if (returnKind != JavaKind.Void) {
                    returnRegister = frameMap.getRegisterConfig().getReturnRegister(returnKind);
                }
                AArch64CalleeSavedRegisters.singleton().emitRestore(masm, totalFrameSize, returnRegister);
            }

            // based on HotSpot's macroAssembler_aarch64.cpp MacroAssembler::remove_frame
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                assert totalFrameSize > 0;
                AArch64Address.AddressingMode addressingMode = AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
                if (AArch64Address.isValidImmediateAddress(64, addressingMode, frameSize)) {
                    masm.ldp(64, fp, lr, AArch64Address.createImmediateAddress(64, addressingMode, sp, frameSize));
                    masm.add(64, sp, sp, totalFrameSize);
                } else {
                    // frameRecordSize = 2 * wordSize (space for fp & lr)
                    int frameRecordSize = totalFrameSize - frameSize;
                    masm.add(64, sp, sp, totalFrameSize - frameRecordSize, scratch);
                    masm.ldp(64, fp, lr, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, sp, frameRecordSize));
                }
            }

            crb.recordMark(SubstrateMarkId.EPILOGUE_INCD_RSP);
        }

        @Override
        public void returned(CompilationResultBuilder crb) {
            crb.recordMark(SubstrateMarkId.EPILOGUE_END);
        }

        @Override
        public boolean hasFrame() {
            return true;
        }
    }

    /**
     * Generates the prolog of a {@link com.oracle.svm.core.deopt.Deoptimizer.StubType#EntryStub}
     * method.
     */
    protected static class DeoptEntryStubContext extends SubstrateAArch64FrameContext {
        protected final CallingConvention callingConvention;

        protected DeoptEntryStubContext(SharedMethod method, CallingConvention callingConvention) {
            super(method);
            this.callingConvention = callingConvention;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            RegisterConfig registerConfig = crb.frameMap.getRegisterConfig();
            Register gpReturnReg = registerConfig.getReturnRegister(JavaKind.Object);
            Register fpReturnReg = registerConfig.getReturnRegister(JavaKind.Double);

            try (ScratchRegister scratch = masm.getScratchRegister()) {
                /* Load DeoptimizedFrame. */
                Register deoptFrameReg = scratch.getRegister();
                assert !deoptFrameReg.equals(gpReturnReg) : "overwriting return reg";
                masm.ldr(64, deoptFrameReg, AArch64Address.createBaseRegisterOnlyAddress(64, registerConfig.getFrameRegister()));

                /* Store the original return value registers. */
                int deoptFrameScratchOffset = DeoptimizedFrame.getScratchSpaceOffset();
                masm.str(64, gpReturnReg, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, deoptFrameReg, deoptFrameScratchOffset));
                masm.fstr(64, fpReturnReg, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, deoptFrameReg, deoptFrameScratchOffset + 8));

                /* Move the DeoptimizedFrame into the first calling convention register. */
                Register firstParameter = ValueUtil.asRegister(callingConvention.getArgument(0));
                masm.mov(64, firstParameter, deoptFrameReg);
            }

            super.enter(crb);
        }
    }

    /**
     * Generates the epilog of a {@link com.oracle.svm.core.deopt.Deoptimizer.StubType#ExitStub}
     * method.
     */
    protected static class DeoptExitStubContext extends SubstrateAArch64FrameContext {
        protected final CallingConvention callingConvention;

        protected DeoptExitStubContext(SharedMethod method, CallingConvention callingConvention) {
            super(method);
            this.callingConvention = callingConvention;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;

            Register firstParameter = ValueUtil.asRegister(callingConvention.getArgument(0));
            /* The new stack pointer is passed in as the first method parameter. */
            masm.mov(64, sp, firstParameter);

            super.enter(crb);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            RegisterConfig registerConfig = crb.frameMap.getRegisterConfig();
            Register gpReturnReg = registerConfig.getReturnRegister(JavaKind.Long);
            Register fpReturnReg = registerConfig.getReturnRegister(JavaKind.Double);

            super.leave(crb);

            /*
             * Restore the return value registers (the DeoptimizedFrame object is initially in
             * gpReturnReg).
             */
            int deoptFrameScratchOffset = DeoptimizedFrame.getScratchSpaceOffset();
            masm.fldr(64, fpReturnReg, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, gpReturnReg, deoptFrameScratchOffset + 8));
            masm.ldr(64, gpReturnReg, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, gpReturnReg, deoptFrameScratchOffset));
        }
    }

    static class SubstrateReferenceMapBuilderFactory implements FrameMap.ReferenceMapBuilderFactory {
        @Override
        public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
            return new SubstrateReferenceMapBuilder(totalFrameSize);
        }
    }

    protected static class SubstrateAArch64MoveFactory extends AArch64MoveFactory {

        private final SharedMethod method;
        private final LIRKindTool lirKindTool;

        protected SubstrateAArch64MoveFactory(SharedMethod method, LIRKindTool lirKindTool) {
            super();
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
                    throw VMError.shouldNotReachHere();
            }
        }

        @Override
        public AArch64LIRInstruction createLoad(AllocatableValue dst, Constant src) {
            if (CompressedNullConstant.COMPRESSED_NULL.equals(src)) {
                return super.createLoad(dst, getZeroConstant(dst));
            } else if (src instanceof SubstrateObjectConstant) {
                return loadObjectConstant(dst, (SubstrateObjectConstant) src);
            }
            return super.createLoad(dst, src);
        }

        @Override
        public LIRInstruction createStackLoad(AllocatableValue dst, Constant src) {
            if (CompressedNullConstant.COMPRESSED_NULL.equals(src)) {
                return super.createStackLoad(dst, getZeroConstant(dst));
            } else if (src instanceof SubstrateObjectConstant) {
                return loadObjectConstant(dst, (SubstrateObjectConstant) src);
            }
            return super.createStackLoad(dst, src);
        }

        protected AArch64LIRInstruction loadObjectConstant(AllocatableValue dst, SubstrateObjectConstant constant) {
            if (ReferenceAccess.singleton().haveCompressedReferences()) {
                RegisterValue heapBase = ReservedRegisters.singleton().getHeapBaseRegister().asValue();
                return new LoadCompressedObjectConstantOp(dst, constant, heapBase, getCompressEncoding(), lirKindTool);
            }
            return new AArch64Move.LoadInlineConstant(constant, dst);
        }
    }

    /*
     * The constant denotes the result produced by this node. Thus if the constant is compressed,
     * the result must be compressed and vice versa. Both compressed and uncompressed constants can
     * be loaded by compiled code.
     *
     * Method getConstant() could uncompress the constant value from the node input. That would
     * require a few indirections and an allocation of an uncompressed constant. The allocation
     * could be eliminated if we stored uncompressed ConstantValue as input. But as this method
     * looks performance-critical, it is still faster to memorize the original constant in the node.
     */
    public static final class LoadCompressedObjectConstantOp extends PointerCompressionOp implements LoadConstantOp {
        public static final LIRInstructionClass<LoadCompressedObjectConstantOp> TYPE = LIRInstructionClass.create(LoadCompressedObjectConstantOp.class);

        static JavaConstant asCompressed(SubstrateObjectConstant constant) {
            // We only want compressed references in code
            return constant.isCompressed() ? constant : constant.compress();
        }

        private final SubstrateObjectConstant constant;

        public LoadCompressedObjectConstantOp(AllocatableValue result, SubstrateObjectConstant constant, AllocatableValue baseRegister, CompressEncoding encoding, LIRKindTool lirKindTool) {
            super(TYPE, result, new ConstantValue(lirKindTool.getNarrowOopKind(), asCompressed(constant)), baseRegister, encoding, true, lirKindTool);
            this.constant = constant;
        }

        @Override
        public Constant getConstant() {
            return constant;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            /*
             * WARNING: must NOT have side effects. Preserve the flags register!
             */
            Register resultReg = getResultRegister();
            int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
            Constant inputConstant = asConstantValue(getInput()).getConstant();
            if (masm.target.inlineObjects) {
                crb.recordInlineDataInCode(inputConstant);
                if (referenceSize == 4) {
                    masm.mov(resultReg, 0xDEADDEAD, true);
                } else {
                    masm.mov(resultReg, 0xDEADDEADDEADDEADL, true);
                }
            } else {
                crb.recordDataReferenceInCode(inputConstant, referenceSize);
                int srcSize = referenceSize * 8;
                masm.adrpLdr(srcSize, resultReg, resultReg);
            }
            if (!constant.isCompressed()) { // the result is expected to be uncompressed
                Register baseReg = getBaseRegister();
                assert !baseReg.equals(Register.None) || getShift() != 0 : "no compression in place";
                masm.add(64, resultReg, baseReg, resultReg, ShiftType.LSL, getShift());
            }
        }
    }

    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AArch64FrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfigNonNull);
    }

    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new AArch64FrameMap(getProviders().getCodeCache(), registerConfig, new SubstrateReferenceMapBuilderFactory());
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenResult, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        Assembler masm = new AArch64MacroAssembler(getTarget());
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
            frameContext = createFrameContext(method);
        }
        LIR lir = lirGenResult.getLIR();
        OptionValues options = lir.getOptions();
        DebugContext debug = lir.getDebug();
        Register uncompressedNullRegister = useLinearPointerCompression() ? ReservedRegisters.singleton().getHeapBaseRegister() : Register.None;
        CompilationResultBuilder crb = factory.createBuilder(getProviders(), lirGenResult.getFrameMap(), masm, dataBuilder, frameContext, options, debug, compilationResult,
                        uncompressedNullRegister);
        crb.setTotalFrameSize(lirGenResult.getFrameMap().totalFrameSize());
        return crb;
    }

    protected FrameContext createFrameContext(SharedMethod method) {
        return new SubstrateAArch64FrameContext(method);
    }

    protected AArch64ArithmeticLIRGenerator createArithmeticLIRGen(AllocatableValue nullRegisterValue) {
        return new AArch64ArithmeticLIRGenerator(nullRegisterValue);
    }

    protected AArch64MoveFactory createMoveFactory(LIRGenerationResult lirGenRes) {
        SharedMethod method = ((SubstrateLIRGenerationResult) lirGenRes).getMethod();
        return new SubstrateAArch64MoveFactory(method, createLirKindTool());
    }

    protected static class SubstrateAArch64LIRKindTool extends AArch64LIRKindTool {
        @Override
        public LIRKind getNarrowOopKind() {
            return LIRKind.compressedReference(AArch64Kind.QWORD);
        }

        @Override
        public LIRKind getNarrowPointerKind() {
            throw VMError.shouldNotReachHere();
        }
    }

    protected AArch64LIRKindTool createLirKindTool() {
        return new SubstrateAArch64LIRKindTool();
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        RegisterValue nullRegisterValue = useLinearPointerCompression() ? ReservedRegisters.singleton().getHeapBaseRegister().asValue(LIRKind.unknownReference(AArch64Kind.QWORD)) : null;
        AArch64ArithmeticLIRGenerator arithmeticLIRGen = createArithmeticLIRGen(nullRegisterValue);
        AArch64MoveFactory moveFactory = createMoveFactory(lirGenRes);
        return new SubstrateAArch64LIRGenerator(createLirKindTool(), arithmeticLIRGen, moveFactory, getProviders(), lirGenRes);
    }

    protected AArch64NodeMatchRules createMatchRules(LIRGeneratorTool lirGen) {
        return new AArch64NodeMatchRules(lirGen);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        AArch64NodeMatchRules nodeMatchRules = createMatchRules(lirGen);
        return new SubstrateAArch64NodeLIRBuilder(graph, lirGen, nodeMatchRules);
    }

    protected static boolean useLinearPointerCompression() {
        return SubstrateOptions.SpawnIsolates.getValue();
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new RegisterAllocationConfig(registerConfigNonNull, allocationRestrictedTo);
    }

    @Override
    public CompilationResult createJNITrampolineMethod(ResolvedJavaMethod method, CompilationIdentifier identifier,
                    RegisterValue threadArg, int threadIsolateOffset, RegisterValue methodIdArg, int methodObjEntryPointOffset) {

        CompilationResult result = new CompilationResult(identifier);
        AArch64MacroAssembler asm = new AArch64MacroAssembler(getTarget());
        try (ScratchRegister scratch = asm.getScratchRegister()) {
            Register scratchRegister = scratch.getRegister();
            if (SubstrateOptions.SpawnIsolates.getValue()) { // method id is offset from heap base
                asm.ldr(64, scratchRegister, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, threadArg.getRegister(), threadIsolateOffset));
                asm.add(64, scratchRegister, scratchRegister, methodIdArg.getRegister());
                asm.ldr(64, scratchRegister, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, scratchRegister, methodObjEntryPointOffset));
            } else { // method id is address of method object
                asm.ldr(64, scratchRegister, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, methodIdArg.getRegister(), methodObjEntryPointOffset));
            }
            asm.jmp(scratchRegister);
        }
        result.recordMark(asm.position(), PROLOGUE_DECD_RSP);
        result.recordMark(asm.position(), PROLOGUE_END);
        byte[] instructions = asm.close(true);
        result.setTargetCode(instructions, instructions.length);
        result.setTotalFrameSize(getTarget().wordSize * 2); // not really, but 0 not allowed
        return result;
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        return new SubstrateCompiledCode(compilationResult);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner) {
        try {
            crb.buildLabelOffsets(lir);
            crb.emit(lir);
        } catch (BranchTargetOutOfBoundsException e) {
            // A branch estimation was wrong, now retry with conservative label ranges, this
            // should always work
            crb.setConservativeLabelRanges();
            crb.resetForEmittingCode();
            lir.resetLabels();
            crb.emit(lir);
        }
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, RegisterAllocationConfig registerAllocationConfig, StructuredGraph graph, Object stub) {
        SharedMethod method = (SharedMethod) graph.method();
        CallingConvention callingConvention = CodeUtil.getCallingConvention(getCodeCache(), method.getCallingConventionKind().toType(false), method, this);
        return new SubstrateLIRGenerationResult(compilationId, lir, newFrameMapBuilder(registerAllocationConfig.getRegisterConfig()), registerAllocationConfig, callingConvention, method);
    }

    @Override
    public BasePhase<CoreProviders> newAddressLoweringPhase(CodeCacheProvider codeCache) {
        return new AddressLoweringByUsePhase(new AArch64AddressLoweringByUse(createLirKindTool(), false));
    }
}
