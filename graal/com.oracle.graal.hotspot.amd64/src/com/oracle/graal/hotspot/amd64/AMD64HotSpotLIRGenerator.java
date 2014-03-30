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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.util.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.amd64.AMD64HotSpotMove.LoadCompressedPointer;
import com.oracle.graal.hotspot.amd64.AMD64HotSpotMove.StoreCompressedConstantOp;
import com.oracle.graal.hotspot.amd64.AMD64HotSpotMove.StoreCompressedPointer;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.NoOp;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Move.LeaDataOp;
import com.oracle.graal.lir.amd64.AMD64Move.LoadOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveFromRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.StoreConstantOp;
import com.oracle.graal.lir.amd64.AMD64Move.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
public class AMD64HotSpotLIRGenerator extends AMD64LIRGenerator implements HotSpotLIRGenerator {

    final HotSpotVMConfig config;

    protected AMD64HotSpotLIRGenerator(HotSpotProviders providers, HotSpotVMConfig config, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(providers, cc, lirGenRes);
        assert config.basicLockSize == 8;
        this.config = config;
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    /**
     * Utility for emitting the instruction to save RBP.
     */
    class SaveRbp {

        final NoOp placeholder;

        /**
         * The slot reserved for saving RBP.
         */
        final StackSlot reservedSlot;

        public SaveRbp(NoOp placeholder) {
            this.placeholder = placeholder;
            this.reservedSlot = res.getFrameMap().allocateSpillSlot(Kind.Long);
            assert reservedSlot.getRawOffset() == -16 : reservedSlot.getRawOffset();
        }

        /**
         * Replaces this operation with the appropriate move for saving rbp.
         *
         * @param useStack specifies if rbp must be saved to the stack
         */
        public AllocatableValue finalize(boolean useStack) {
            AllocatableValue dst;
            if (useStack) {
                dst = reservedSlot;
            } else {
                res.getFrameMap().freeSpillSlot(reservedSlot);
                dst = newVariable(Kind.Long);
            }

            placeholder.replace(res.getLIR(), new MoveFromRegOp(dst, rbp.asValue(Kind.Long)));
            return dst;
        }
    }

    SaveRbp saveRbp;

    /**
     * List of epilogue operations that need to restore RBP.
     */
    List<AMD64HotSpotEpilogueOp> epilogueOps = new ArrayList<>(2);

    @Override
    public void append(LIRInstruction op) {
        super.append(op);
        if (op instanceof AMD64HotSpotEpilogueOp) {
            epilogueOps.add((AMD64HotSpotEpilogueOp) op);
        }
    }

    @Override
    public StackSlot getLockSlot(int lockDepth) {
        return ((HotSpotDebugInfoBuilder) getDebugInfoBuilder()).lockStack().makeLockSlot(lockDepth);
    }

    private Register findPollOnReturnScratchRegister() {
        RegisterConfig regConfig = getProviders().getCodeCache().getRegisterConfig();
        for (Register r : regConfig.getAllocatableRegisters(Kind.Long)) {
            if (r != regConfig.getReturnRegister(Kind.Long) && r != AMD64.rbp) {
                return r;
            }
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    private Register pollOnReturnScratchRegister;

    @Override
    public void emitReturn(Value input) {
        if (pollOnReturnScratchRegister == null) {
            pollOnReturnScratchRegister = findPollOnReturnScratchRegister();
        }
        append(new AMD64HotSpotReturnOp(input, getStub() != null, pollOnReturnScratchRegister));
    }

    @Override
    protected boolean needOnlyOopMaps() {
        // Stubs only need oop maps
        return ((AMD64HotSpotLIRGenerationResult) res).getStub() != null;
    }

    @Override
    public void emitData(AllocatableValue dst, byte[] data) {
        append(new LeaDataOp(dst, data));
    }

    private LIRFrameState currentRuntimeCallInfo;

    @Override
    protected void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        currentRuntimeCallInfo = info;
        super.emitForeignCall(linkage, result, arguments, temps, info);
    }

    protected AMD64SaveRegistersOp emitSaveRegisters(Register[] savedRegisters, StackSlot[] savedRegisterLocations) {
        AMD64SaveRegistersOp save = new AMD64SaveRegistersOp(savedRegisters, savedRegisterLocations, true);
        append(save);
        return save;
    }

    protected void emitRestoreRegisters(AMD64SaveRegistersOp save) {
        append(new AMD64RestoreRegistersOp(save.getSlots().clone(), save));
    }

    public Stub getStub() {
        return ((AMD64HotSpotLIRGenerationResult) res).getStub();
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, DeoptimizingNode info, Value... args) {
        boolean destroysRegisters = linkage.destroysRegisters();

        AMD64SaveRegistersOp save = null;
        StackSlot[] savedRegisterLocations = null;
        if (destroysRegisters) {
            if (getStub() != null) {
                if (getStub().preservesRegisters()) {
                    Register[] savedRegisters = res.getFrameMap().registerConfig.getAllocatableRegisters();
                    savedRegisterLocations = new StackSlot[savedRegisters.length];
                    for (int i = 0; i < savedRegisters.length; i++) {
                        PlatformKind kind = target().arch.getLargestStorableKind(savedRegisters[i].getRegisterCategory());
                        assert kind != Kind.Illegal;
                        StackSlot spillSlot = res.getFrameMap().allocateSpillSlot(kind);
                        savedRegisterLocations[i] = spillSlot;
                    }
                    save = emitSaveRegisters(savedRegisters, savedRegisterLocations);
                }
            }
        }

        Variable result;

        if (linkage.canDeoptimize()) {
            assert info != null || ((AMD64HotSpotLIRGenerationResult) res).getStub() != null;
            Register thread = getProviders().getRegisters().getThreadRegister();
            append(new AMD64HotSpotCRuntimeCallPrologueOp(config.threadLastJavaSpOffset(), thread));
            result = super.emitForeignCall(linkage, info, args);
            append(new AMD64HotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset(), config.threadLastJavaFpOffset(), thread));
        } else {
            result = super.emitForeignCall(linkage, info, args);
        }

        if (destroysRegisters) {
            if (getStub() != null) {
                if (getStub().preservesRegisters()) {
                    assert !((AMD64HotSpotLIRGenerationResult) res).getCalleeSaveInfo().containsKey(currentRuntimeCallInfo);
                    ((AMD64HotSpotLIRGenerationResult) res).getCalleeSaveInfo().put(currentRuntimeCallInfo, save);

                    emitRestoreRegisters(save);
                } else {
                    assert zapRegisters();
                }
            }
        }

        return result;
    }

    protected AMD64ZapRegistersOp emitZapRegisters(Register[] zappedRegisters, Constant[] zapValues) {
        AMD64ZapRegistersOp zap = new AMD64ZapRegistersOp(zappedRegisters, zapValues);
        append(zap);
        return zap;
    }

    protected boolean zapRegisters() {
        Register[] zappedRegisters = res.getFrameMap().registerConfig.getAllocatableRegisters();
        Constant[] zapValues = new Constant[zappedRegisters.length];
        for (int i = 0; i < zappedRegisters.length; i++) {
            PlatformKind kind = target().arch.getLargestStorableKind(zappedRegisters[i].getRegisterCategory());
            assert kind != Kind.Illegal;
            zapValues[i] = zapValueForKind(kind);
        }
        ((AMD64HotSpotLIRGenerationResult) res).getCalleeSaveInfo().put(currentRuntimeCallInfo, emitZapRegisters(zappedRegisters, zapValues));
        return true;
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        append(new AMD64TailcallOp(args, address));
    }

    @Override
    public void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments) {
        Value[] argLocations = new Value[args.length];
        res.getFrameMap().callsMethod(nativeCallingConvention);
        // TODO(mg): in case a native function uses floating point varargs, the ABI requires that
        // RAX contains the length of the varargs
        AllocatableValue numberOfFloatingPointArgumentsRegister = AMD64.rax.asValue();
        emitMove(numberOfFloatingPointArgumentsRegister, Constant.forInt(numberOfFloatingPointArguments));
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = nativeCallingConvention.getArgument(i);
            emitMove(loc, arg);
            argLocations[i] = loc;
        }
        Value ptr = emitMove(Constant.forLong(address));
        append(new AMD64CCall(nativeCallingConvention.getReturn(), ptr, numberOfFloatingPointArgumentsRegister, argLocations));
    }

    @Override
    public void emitUnwind(Value exception) {
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionParameter = (RegisterValue) outgoingCc.getArgument(0);
        emitMove(exceptionParameter, exception);
        append(new AMD64HotSpotUnwindOp(exceptionParameter));
    }

    private void moveDeoptValuesToThread(Value actionAndReason, Value speculation) {
        moveValueToThread(actionAndReason, runtime().getConfig().pendingDeoptimizationOffset);
        moveValueToThread(speculation, runtime().getConfig().pendingFailedSpeculationOffset);
    }

    private void moveValueToThread(Value v, int offset) {
        Kind wordKind = getProviders().getCodeCache().getTarget().wordKind;
        RegisterValue thread = getProviders().getRegisters().getThreadRegister().asValue(wordKind);
        AMD64AddressValue address = new AMD64AddressValue(v.getKind(), thread, offset);
        emitStore(v.getKind(), address, v, null);
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, DeoptimizingNode deopting) {
        moveDeoptValuesToThread(actionAndReason, speculation);
        append(new AMD64DeoptimizeOp(state(deopting)));
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        moveDeoptValuesToThread(getMetaAccess().encodeDeoptActionAndReason(action, reason, 0), Constant.NULL_OBJECT);
        append(new AMD64HotSpotDeoptimizeCallerOp());
    }

    @Override
    public void beforeRegisterAllocation() {
        super.beforeRegisterAllocation();
        boolean hasDebugInfo = res.getLIR().hasDebugInfo();
        AllocatableValue savedRbp = saveRbp.finalize(hasDebugInfo);
        if (hasDebugInfo) {
            ((AMD64HotSpotLIRGenerationResult) res).setDeoptimizationRescueSlot(res.getFrameMap().allocateSpillSlot(Kind.Long));
        }

        for (AMD64HotSpotEpilogueOp op : epilogueOps) {
            op.savedRbp = savedRbp;
        }
    }

    /**
     * Returns whether or not the input access should be (de)compressed.
     */
    private boolean isCompressedOperation(Kind kind, Access access) {
        return access != null && access.isCompressible() && ((kind == Kind.Long && config.useCompressedClassPointers) || (kind == Kind.Object && config.useCompressedOops));
    }

    /**
     * @return a compressed version of the incoming constant
     */
    protected static Constant compress(Constant c, CompressEncoding encoding) {
        if (c.getKind() == Kind.Long) {
            return Constant.forIntegerKind(Kind.Int, (int) (((c.asLong() - encoding.base) >> encoding.shift) & 0xffffffffL), c.getPrimitiveAnnotation());
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitLoad(Kind kind, Value address, Access access) {
        AMD64AddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(kind.getStackKind());
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        /**
         * Currently, the (de)compression of pointers applies conditionally to some objects (oops,
         * kind==Object) and some addresses (klass pointers, kind==Long). Initially, the input
         * operation is checked to discover if it has been tagged as a potential "compression"
         * candidate. Consequently, depending on the appropriate kind, the specific (de)compression
         * functions are being called.
         */
        if (isCompressedOperation(kind, access)) {
            if (kind == Kind.Object) {
                append(new LoadCompressedPointer(kind, result, getProviders().getRegisters().getHeapBaseRegister().asValue(), loadAddress, state, config.getOopEncoding()));
            } else if (kind == Kind.Long) {
                Variable scratch = config.getKlassEncoding().base != 0 ? newVariable(Kind.Long) : null;
                append(new LoadCompressedPointer(kind, result, scratch, loadAddress, state, config.getKlassEncoding()));
            } else {
                throw GraalInternalError.shouldNotReachHere("can't handle: " + access);
            }
        } else {
            append(new LoadOp(kind, result, loadAddress, state));
        }
        return result;
    }

    @Override
    public void emitStore(Kind kind, Value address, Value inputVal, Access access) {
        AMD64AddressValue storeAddress = asAddressValue(address);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        boolean isCompressed = isCompressedOperation(kind, access);
        if (isConstant(inputVal)) {
            Constant c = asConstant(inputVal);
            if (canStoreConstant(c, isCompressed)) {
                if (isCompressed) {
                    if (c.getKind() == Kind.Object) {
                        append(new StoreCompressedConstantOp(kind, storeAddress, c, state));
                    } else if (c.getKind() == Kind.Long) {
                        // It's always a good idea to directly store compressed constants since they
                        // have to be materialized as 64 bits encoded otherwise.
                        Constant value = compress(c, config.getKlassEncoding());
                        append(new StoreCompressedConstantOp(kind, storeAddress, value, state));
                    } else {
                        throw GraalInternalError.shouldNotReachHere("can't handle: " + access);
                    }
                    return;
                } else {
                    append(new StoreConstantOp(kind, storeAddress, c, state));
                    return;
                }
            }
        }
        Variable input = load(inputVal);
        if (isCompressed) {
            if (kind == Kind.Object) {
                if (input.getKind() == Kind.Object) {
                    Variable scratch = newVariable(Kind.Long);
                    Register heapBaseReg = getProviders().getRegisters().getHeapBaseRegister();
                    append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, config.getOopEncoding(), heapBaseReg));
                } else {
                    // the input oop is already compressed
                    append(new StoreOp(input.getKind(), storeAddress, input, state));
                }
            } else if (kind == Kind.Long) {
                Variable scratch = newVariable(Kind.Long);
                Register heapBaseReg = getProviders().getRegisters().getHeapBaseRegister();
                append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, config.getKlassEncoding(), heapBaseReg));
            } else {
                append(new StoreOp(kind, storeAddress, input, state));
            }
        } else {
            append(new StoreOp(kind, storeAddress, input, state));
        }
    }

}
