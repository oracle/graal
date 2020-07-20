/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.util.VMError.unimplemented;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.differentRegisters;

import java.util.Collection;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.amd64.AMD64ArithmeticLIRGenerator;
import org.graalvm.compiler.core.amd64.AMD64LIRGenerator;
import org.graalvm.compiler.core.amd64.AMD64LIRKindTool;
import org.graalvm.compiler.core.amd64.AMD64MoveFactory;
import org.graalvm.compiler.core.amd64.AMD64MoveFactoryBase;
import org.graalvm.compiler.core.amd64.AMD64MoveFactoryBase.BackupSlotProvider;
import org.graalvm.compiler.core.amd64.AMD64NodeLIRBuilder;
import org.graalvm.compiler.core.amd64.AMD64NodeMatchRules;
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
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.NodeValueMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.code.PatchConsumerFactory;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
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

    @Opcode("CALL_DIRECT")
    public static class SubstrateAMD64DirectCallOp extends AMD64Call.DirectCallOp {
        public static final LIRInstructionClass<SubstrateAMD64DirectCallOp> TYPE = LIRInstructionClass.create(SubstrateAMD64DirectCallOp.class);

        private final RuntimeConfiguration runtimeConfiguration;
        private final int newThreadStatus;
        @Use({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchor;
        @Temp({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchorTemp;

        private final boolean destroysCallerSavedRegisters;
        @Temp({REG, OperandFlag.ILLEGAL}) private Value exceptionTemp;

        public SubstrateAMD64DirectCallOp(RuntimeConfiguration runtimeConfiguration, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state,
                        Value javaFrameAnchor, Value javaFrameAnchorTemp, int newThreadStatus, boolean destroysCallerSavedRegisters, Value exceptionTemp) {
            super(TYPE, callTarget, result, parameters, temps, state);
            this.runtimeConfiguration = runtimeConfiguration;
            this.newThreadStatus = newThreadStatus;
            this.javaFrameAnchor = javaFrameAnchor;
            this.javaFrameAnchorTemp = javaFrameAnchorTemp;
            this.destroysCallerSavedRegisters = destroysCallerSavedRegisters;
            this.exceptionTemp = exceptionTemp;

            assert differentRegisters(parameters, temps, javaFrameAnchor, javaFrameAnchorTemp);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            maybeTransitionToNative(crb, masm, runtimeConfiguration, javaFrameAnchor, javaFrameAnchorTemp, state, newThreadStatus);
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

        private final RuntimeConfiguration runtimeConfiguration;
        private final int newThreadStatus;
        @Use({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchor;
        @Temp({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchorTemp;

        private final boolean destroysCallerSavedRegisters;
        @Temp({REG, OperandFlag.ILLEGAL}) private Value exceptionTemp;

        public SubstrateAMD64IndirectCallOp(RuntimeConfiguration runtimeConfiguration, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress,
                        LIRFrameState state, Value javaFrameAnchor, Value javaFrameAnchorTemp, int newThreadStatus, boolean destroysCallerSavedRegisters, Value exceptionTemp) {
            super(TYPE, callTarget, result, parameters, temps, targetAddress, state);
            this.runtimeConfiguration = runtimeConfiguration;
            this.newThreadStatus = newThreadStatus;
            this.javaFrameAnchor = javaFrameAnchor;
            this.javaFrameAnchorTemp = javaFrameAnchorTemp;
            this.destroysCallerSavedRegisters = destroysCallerSavedRegisters;
            this.exceptionTemp = exceptionTemp;

            assert differentRegisters(parameters, temps, targetAddress, javaFrameAnchor, javaFrameAnchorTemp);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            maybeTransitionToNative(crb, masm, runtimeConfiguration, javaFrameAnchor, javaFrameAnchorTemp, state, newThreadStatus);
            AMD64Call.indirectCall(crb, masm, asRegister(targetAddress), callTarget, state);
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return destroysCallerSavedRegisters;
        }
    }

    static void maybeTransitionToNative(CompilationResultBuilder crb, AMD64MacroAssembler masm, RuntimeConfiguration runtimeConfiguration, Value javaFrameAnchor, Value temp, LIRFrameState state,
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

        masm.movq(new AMD64Address(anchor, runtimeConfiguration.getJavaFrameAnchorLastIPOffset()), lastJavaIP);
        masm.movq(new AMD64Address(anchor, runtimeConfiguration.getJavaFrameAnchorLastSPOffset()), AMD64.rsp);

        if (SubstrateOptions.MultiThreaded.getValue()) {
            /* Change the VMThread status from Java to Native. */
            masm.movl(new AMD64Address(runtimeConfiguration.getThreadRegister(), runtimeConfiguration.getVMThreadStatusOffset()), newThreadStatus);
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
            /*
             * We could add some code in debug builds here checking that it is really unreachable.
             */
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
                int registerSaveAreaSizeInBytes = method.hasCalleeSavedRegisters() ? calleeSavedRegisters.getSaveAreaSize() : 0;
                StackSlot calleeSaveArea = frameMap.allocateStackSlots(registerSaveAreaSizeInBytes / frameMap.getTarget().wordSize);

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

    protected class SubstrateAMD64LIRGenerator extends AMD64LIRGenerator implements SubstrateLIRGenerator {

        public SubstrateAMD64LIRGenerator(LIRKindTool lirKindTool, AMD64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, Providers providers, LIRGenerationResult lirGenRes) {
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

        private Register getHeapBaseRegister() {
            return getRegisterConfig().getHeapBaseRegister();
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
        protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
            SubstrateForeignCallLinkage callTarget = (SubstrateForeignCallLinkage) linkage;
            ResolvedJavaMethod targetMethod = callTarget.getMethod();
            append(new SubstrateAMD64DirectCallOp(getRuntimeConfiguration(), targetMethod, result, arguments, temps, info, Value.ILLEGAL, Value.ILLEGAL, StatusSupport.STATUS_ILLEGAL,
                            getDestroysCallerSavedRegisters(targetMethod), Value.ILLEGAL));
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
        public void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments) {
            throw unimplemented();
        }

        @Override
        public void emitVerificationMarker(Object marker) {
            append(new VerificationMarkerOp(marker));
        }

        @Override
        public void emitInstructionSynchronizationBarrier() {
            throw shouldNotReachHere("AMD64 does not need instruction synchronization");
        }

        // private static LIRKind toStackKind(LIRKind kind) {
        // if (kind.getPlatformKind() instanceof Kind) {
        // Kind stackKind = ((Kind) kind.getPlatformKind()).getStackKind();
        // return kind.changeType(stackKind);
        // } else {
        // return kind;
        // }
        // }
        //
        // @Override
        // public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
        // AMD64AddressValue loadAddress = asAddressValue(address);
        // Variable result = newVariable(toStackKind(kind));
        // append(new LoadOp((Kind) kind.getPlatformKind(), result, loadAddress, state));
        // return result;
        // }
        //
        // @Override
        // public void emitStore(LIRKind kind, Value address, Value inputVal, LIRFrameState state) {
        // AMD64AddressValue storeAddress = asAddressValue(address);
        // if (isConstant(inputVal)) {
        // JavaConstant c = asConstant(inputVal);
        // if (canStoreConstant(c)) {
        // append(new StoreConstantOp((Kind) kind.getPlatformKind(), storeAddress, c, state));
        // return;
        // }
        // }
        // Variable input = load(inputVal);
        // append(new StoreOp((Kind) kind.getPlatformKind(), storeAddress, input, state));
        //
        // }
        //
        // @Override
        // public Value emitCompareAndSwap(Value address, Value expectedValue, Value newValue, Value
        // trueValue, Value falseValue) {
        // LIRKind kind = newValue.getLIRKind();
        // assert kind.equals(expectedValue.getLIRKind());
        // Kind memKind = (Kind) kind.getPlatformKind();
        //
        // AMD64AddressValue addressValue = asAddressValue(address);
        // RegisterValue raxRes = AMD64.rax.asValue(kind);
        // emitMove(raxRes, expectedValue);
        // append(new CompareAndSwapOp(memKind, raxRes, addressValue, raxRes,
        // asAllocatable(newValue)));
        //
        // assert trueValue.getLIRKind().equals(falseValue.getLIRKind());
        // Variable result = newVariable(trueValue.getLIRKind());
        // append(new CondMoveOp(result, Condition.EQ, asAllocatable(trueValue), falseValue));
        // return result;
        // }
        //
        // @Override
        // public Value emitAtomicReadAndAdd(Value address, Value delta) {
        // LIRKind kind = delta.getLIRKind();
        // Kind memKind = (Kind) kind.getPlatformKind();
        // Variable result = newVariable(kind);
        // AMD64AddressValue addressValue = asAddressValue(address);
        // append(new AMD64Move.AtomicReadAndAddOp(memKind, result, addressValue,
        // asAllocatable(delta)));
        // return result;
        // }
        //
        // @Override
        // public Value emitAtomicReadAndWrite(Value address, Value newValue) {
        // LIRKind kind = newValue.getLIRKind();
        // Kind memKind = (Kind) kind.getPlatformKind();
        // Variable result = newVariable(kind);
        // AMD64AddressValue addressValue = asAddressValue(address);
        // append(new AMD64Move.AtomicReadAndWriteOp(memKind, result, addressValue,
        // asAllocatable(newValue)));
        // return result;
        // }
        //
        // @Override
        // public void emitNullCheck(Value address, LIRFrameState state) {
        // if (address.getValueKind().getPlatformKind() == AMD64Kind.DWORD) {
        // CompressEncoding encoding = compressEncoding;
        // Value uncompressed;
        // if (encoding.getShift() <= 3) {
        // LIRKind wordKind = LIRKind.unknownReference(target().arch.getWordKind());
        // uncompressed = new AMD64AddressValue(wordKind, getHeapBaseRegister().asValue(wordKind),
        // asAllocatable(address), AMD64Address.Scale.fromInt(1 << encoding.getShift()), 0);
        // } else {
        // uncompressed = emitUncompress(address, encoding, false);
        // }
        // append(new AMD64Move.NullCheckOp(asAddressValue(uncompressed), state));
        // return;
        // }
        // super.emitNullCheck(address, state);
        // }

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
            append(new AMD64Move.CompressPointerOp(result, asAllocatable(pointer), getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        }

        @Override
        public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean isNonNull) {
            assert pointer.getValueKind(LIRKind.class).getPlatformKind() == getLIRKindTool().getNarrowOopKind().getPlatformKind();
            Variable result = newVariable(getLIRKindTool().getObjectKind());
            boolean nonNull = useLinearPointerCompression() || isNonNull;
            append(new AMD64Move.UncompressPointerOp(result, asAllocatable(pointer), getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        }

        @Override
        public void emitConvertNullToZero(AllocatableValue result, Value value) {
            if (useLinearPointerCompression()) {
                append(new AMD64Move.ConvertNullToZeroOp(result, (AllocatableValue) value));
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
    }

    public final class SubstrateAMD64NodeLIRBuilder extends AMD64NodeLIRBuilder implements SubstrateNodeLIRBuilder {

        public SubstrateAMD64NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AMD64NodeMatchRules nodeMatchRules) {
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

            CallingConvention convention = gen.getRegisterConfig().getCallingConvention(SubstrateCallingConventionType.JavaCall, null, sig, gen);
            append(new AMD64BreakpointOp(visitInvokeArguments(convention, node.arguments())));
        }

        @Override
        protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
            return new SubstrateDebugInfoBuilder(graph, gen.getProviders().getMetaAccessExtensionProvider(), nodeValueMap);
        }

        @Override
        public Value[] visitInvokeArguments(CallingConvention invokeCc, Collection<ValueNode> arguments) {
            Value[] values = super.visitInvokeArguments(invokeCc, arguments);

            SubstrateCallingConventionType type = (SubstrateCallingConventionType) ((SubstrateCallingConvention) invokeCc).getType();
            if (type.nativeABI) {
                // Native functions might have varargs, in which case we need to set %al to the
                // number of XMM registers used for passing arguments
                int xmmCount = 0;
                for (Value v : values) {
                    if (isRegister(v) && asRegister(v).getRegisterCategory().equals(AMD64.XMM)) {
                        xmmCount++;
                    }
                }
                assert xmmCount <= 8;
                AllocatableValue xmmCountRegister = AMD64.rax.asValue(LIRKind.value(AMD64Kind.DWORD));
                gen.emitMoveConstant(xmmCountRegister, JavaConstant.forInt(xmmCount));
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
            if (callTarget.invoke() instanceof InvokeWithExceptionNode) {
                return gen.getRegisterConfig().getReturnRegister(JavaKind.Object).asValue();
            } else {
                return Value.ILLEGAL;
            }
        }

        @Override
        protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            append(new SubstrateAMD64DirectCallOp(getRuntimeConfiguration(), targetMethod, result, parameters, temps, callState,
                            setupJavaFrameAnchor(callTarget), setupJavaFrameAnchorTemp(callTarget), getNewThreadStatus(callTarget),
                            getDestroysCallerSavedRegisters(targetMethod), getExceptionTemp(callTarget)));
        }

        @Override
        protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            // The register allocator cannot handle variables at call sites, need a fixed register.
            Register targetRegister = AMD64.rax;
            if (((SubstrateCallingConventionType) callTarget.callType()).nativeABI) {
                // Do not use RAX for C calls, it contains the number of XMM registers for varargs.
                targetRegister = AMD64.r10;
            }
            AllocatableValue targetAddress = targetRegister.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRGeneratorTool().getLIRKindTool()));
            gen.emitMove(targetAddress, operand(callTarget.computedAddress()));
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            append(new SubstrateAMD64IndirectCallOp(getRuntimeConfiguration(), targetMethod, result, parameters, temps, targetAddress, callState,
                            setupJavaFrameAnchor(callTarget), setupJavaFrameAnchorTemp(callTarget), getNewThreadStatus(callTarget),
                            getDestroysCallerSavedRegisters(targetMethod), getExceptionTemp(callTarget)));
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
    }

    protected static class SubstrateAMD64FrameContext implements FrameContext {

        @Override
        public void enter(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            int frameSize = tasm.frameMap.frameSize();

            if (((SubstrateAMD64RegisterConfig) tasm.frameMap.getRegisterConfig()).shouldUseBasePointer()) {
                /*
                 * Note that we never use the `enter` instruction so that we have a predictable code
                 * pattern at each method prologue. And `enter` seems to be slower than the explicit
                 * code.
                 */
                asm.push(rbp);
                asm.movq(rbp, rsp);
            }
            asm.decrementq(rsp, frameSize);

            tasm.recordMark(PROLOGUE_DECD_RSP);
            tasm.recordMark(PROLOGUE_END);
        }

        @Override
        public void leave(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            int frameSize = tasm.frameMap.frameSize();

            tasm.recordMark(SubstrateMarkId.EPILOGUE_START);

            if (((SubstrateAMD64RegisterConfig) tasm.frameMap.getRegisterConfig()).shouldUseBasePointer()) {
                asm.movq(rsp, rbp);
                asm.pop(rbp);
            } else {
                asm.incrementq(rsp, frameSize);
            }

            tasm.recordMark(SubstrateMarkId.EPILOGUE_INCD_RSP);
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

    static class AMD64StubCallingConventionSubstrateFrameContext extends SubstrateAMD64FrameContext {

        private final JavaKind returnKind;

        AMD64StubCallingConventionSubstrateFrameContext(JavaKind returnKind) {
            this.returnKind = returnKind;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            super.enter(crb);

            AMD64CalleeSavedRegisters.singleton().emitSave((AMD64MacroAssembler) crb.asm, crb.frameMap.totalFrameSize());
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            Register returnRegister = null;
            if (returnKind != JavaKind.Void) {
                returnRegister = crb.frameMap.getRegisterConfig().getReturnRegister(returnKind);
            }
            AMD64CalleeSavedRegisters.singleton().emitRestore((AMD64MacroAssembler) crb.asm, crb.frameMap.totalFrameSize(), returnRegister);

            super.leave(crb);
        }
    }

    /**
     * Generates the prolog of a {@link com.oracle.svm.core.deopt.Deoptimizer.StubType#EntryStub}
     * method.
     */
    protected static class DeoptEntryStubContext extends SubstrateAMD64FrameContext {
        @Override
        public void enter(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            RegisterConfig registerConfig = tasm.frameMap.getRegisterConfig();

            /* Move the DeoptimizedFrame into the first calling convention register. */
            Register deoptimizedFrame = registerConfig.getCallingConventionRegisters(SubstrateCallingConventionType.JavaCall, tasm.target.wordJavaKind).get(0);
            asm.movq(deoptimizedFrame, new AMD64Address(registerConfig.getFrameRegister(), 0));

            /* Store the original return value registers. */
            int scratchOffset = DeoptimizedFrame.getScratchSpaceOffset();
            asm.movq(new AMD64Address(deoptimizedFrame, scratchOffset), registerConfig.getReturnRegister(JavaKind.Long));
            asm.movq(new AMD64Address(deoptimizedFrame, scratchOffset + 8), registerConfig.getReturnRegister(JavaKind.Double));

            super.enter(tasm);
        }
    }

    /**
     * Generates the epilog of a {@link com.oracle.svm.core.deopt.Deoptimizer.StubType#ExitStub}
     * method.
     */
    protected static class DeoptExitStubContext extends SubstrateAMD64FrameContext {
        @Override
        public void enter(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;

            /* The new stack pointer is passed in as the first method parameter. */
            Register firstParameter = tasm.frameMap.getRegisterConfig().getCallingConventionRegisters(SubstrateCallingConventionType.JavaCall, tasm.target.wordJavaKind).get(0);
            asm.movq(rsp, firstParameter);
            /*
             * Compensate that we set the stack pointer after the return address was pushed. Note
             * that the "new" frame location does not have a valid return address at this point.
             * That is OK because the return address for the deoptimization target frame will be
             * patched into this location.
             */
            asm.subq(rsp, FrameAccess.returnAddressSize());

            super.enter(tasm);
        }

        @Override
        public void leave(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;

            super.leave(tasm);

            // Restore the return value registers (the DeoptimizedFrame is in rax).
            int scratchOffset = DeoptimizedFrame.getScratchSpaceOffset();
            asm.movq(xmm0, new AMD64Address(rax, scratchOffset + 8));
            asm.movq(rax, new AMD64Address(rax, scratchOffset));
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
        private final LIRKindTool lirKindTool;
        private final SubstrateRegisterConfig registerConfig;

        protected SubstrateAMD64MoveFactory(BackupSlotProvider backupSlotProvider, SharedMethod method, LIRKindTool lirKindTool, SubstrateRegisterConfig registerConfig) {
            super(backupSlotProvider);
            this.method = method;
            this.lirKindTool = lirKindTool;
            this.registerConfig = registerConfig;
        }

        @Override
        public boolean allowConstantToStackMove(Constant constant) {
            if (constant instanceof SubstrateObjectConstant && method.isDeoptTarget()) {
                return false;
            }
            return super.allowConstantToStackMove(constant);
        }

        @Override
        public AMD64LIRInstruction createLoad(AllocatableValue dst, Constant src) {
            if (CompressedNullConstant.COMPRESSED_NULL.equals(src)) {
                return super.createLoad(dst, JavaConstant.INT_0);
            } else if (src instanceof SubstrateObjectConstant) {
                return loadObjectConstant(dst, (SubstrateObjectConstant) src);
            }
            return super.createLoad(dst, src);
        }

        @Override
        public LIRInstruction createStackLoad(AllocatableValue dst, Constant src) {
            if (CompressedNullConstant.COMPRESSED_NULL.equals(src)) {
                return super.createStackLoad(dst, JavaConstant.INT_0);
            } else if (src instanceof SubstrateObjectConstant) {
                return loadObjectConstant(dst, (SubstrateObjectConstant) src);
            }
            return super.createStackLoad(dst, src);
        }

        protected AMD64LIRInstruction loadObjectConstant(AllocatableValue dst, SubstrateObjectConstant constant) {
            if (ReferenceAccess.singleton().haveCompressedReferences()) {
                RegisterValue heapBase = registerConfig.getHeapBaseRegister().asValue();
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

            static JavaConstant asCompressed(SubstrateObjectConstant constant) {
                // We only want compressed references in code
                return constant.isCompressed() ? constant : constant.compress();
            }

            private final SubstrateObjectConstant constant;

            LoadCompressedObjectConstantOp(AllocatableValue result, SubstrateObjectConstant constant, AllocatableValue baseRegister, CompressEncoding encoding, LIRKindTool lirKindTool) {
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
                if (masm.target.inlineObjects) {
                    crb.recordInlineDataInCode(inputConstant);
                    masm.movq(resultReg, 0xDEADDEADDEADDEADL, true);
                } else {
                    AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(inputConstant, referenceSize);
                    masm.movq(resultReg, address);
                }
                if (!constant.isCompressed()) { // the result is expected to be uncompressed
                    Register baseReg = getBaseRegister(crb);
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
        CallingConvention callingConvention = CodeUtil.getCallingConvention(getCodeCache(), method.isEntryPoint() ? SubstrateCallingConventionType.NativeCallee
                        : SubstrateCallingConventionType.JavaCallee, method, this);
        return new SubstrateLIRGenerationResult(compilationId, lir, newFrameMapBuilder(registerAllocationConfig.getRegisterConfig()), callingConvention, registerAllocationConfig, method);
    }

    protected AMD64ArithmeticLIRGenerator createArithmeticLIRGen(RegisterValue nullRegisterValue) {
        return new AMD64ArithmeticLIRGenerator(nullRegisterValue);
    }

    protected static SubstrateAMD64RegisterConfig getRegisterConfig(LIRGenerationResult lirGenRes) {
        return (SubstrateAMD64RegisterConfig) lirGenRes.getRegisterConfig();
    }

    protected static Register getHeapBaseRegister(LIRGenerationResult lirGenRes) {
        return getRegisterConfig(lirGenRes).getHeapBaseRegister();
    }

    protected AMD64MoveFactoryBase createMoveFactory(LIRGenerationResult lirGenRes, BackupSlotProvider backupSlotProvider) {
        SharedMethod method = ((SubstrateLIRGenerationResult) lirGenRes).getMethod();
        return new SubstrateAMD64MoveFactory(backupSlotProvider, method, createLirKindTool(), getRegisterConfig(lirGenRes));
    }

    protected static class SubstrateAMD64LIRKindTool extends AMD64LIRKindTool {
        @Override
        public LIRKind getNarrowOopKind() {
            return LIRKind.compressedReference(AMD64Kind.QWORD);
        }

        @Override
        public LIRKind getNarrowPointerKind() {
            throw VMError.shouldNotReachHere();
        }
    }

    protected LIRKindTool createLirKindTool() {
        return new SubstrateAMD64LIRKindTool();
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        RegisterValue nullRegisterValue = useLinearPointerCompression() ? getHeapBaseRegister(lirGenRes).asValue() : null;
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
        AMD64MacroAssembler masm = new AMD64MacroAssembler(getTarget(), options);
        masm.setCodePatchShifter(compilationResult::shiftCodePatch);
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
        final FrameContext frameContext;
        if (stubType == Deoptimizer.StubType.EntryStub) {
            frameContext = new DeoptEntryStubContext();
        } else if (stubType == Deoptimizer.StubType.ExitStub) {
            frameContext = new DeoptExitStubContext();
        } else if (method.hasCalleeSavedRegisters()) {
            VMError.guarantee(!method.isDeoptTarget(), "Deoptimization runtime cannot fill the callee saved registers");
            frameContext = new AMD64StubCallingConventionSubstrateFrameContext(method.getSignature().getReturnKind());
        } else {
            frameContext = new SubstrateAMD64FrameContext();
        }
        DebugContext debug = lir.getDebug();
        Register uncompressedNullRegister = useLinearPointerCompression() ? getHeapBaseRegister(lirGenResult) : Register.None;
        CompilationResultBuilder tasm = factory.createBuilder(getCodeCache(), getForeignCalls(), lirGenResult.getFrameMap(), masm, dataBuilder, frameContext, options, debug, compilationResult,
                        uncompressedNullRegister);
        tasm.setTotalFrameSize(lirGenResult.getFrameMap().totalFrameSize());
        return tasm;
    }

    @Override
    public Phase newAddressLoweringPhase(CodeCacheProvider codeCache) {
        CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);
        SubstrateRegisterConfig registerConfig = (SubstrateRegisterConfig) codeCache.getRegisterConfig();
        return new AddressLoweringPhase(new SubstrateAMD64AddressLowering(compressEncoding, registerConfig));
    }

    @Override
    public CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        return new SubstrateCompiledCode(compilationResult);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner) {
        crb.emit(lir);
    }

    @Override
    public CompilationResult createJNITrampolineMethod(ResolvedJavaMethod method, CompilationIdentifier identifier,
                    RegisterValue threadArg, int threadIsolateOffset, RegisterValue methodIdArg, int methodObjEntryPointOffset) {

        CompilationResult result = new CompilationResult(identifier);
        AMD64Assembler asm = new AMD64Assembler(getTarget());
        if (SubstrateOptions.SpawnIsolates.getValue()) { // method id is offset from heap base
            asm.movq(rax, new AMD64Address(threadArg.getRegister(), threadIsolateOffset));
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
