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
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.sparc.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.lir.sparc.SPARCMove.LoadOp;
import com.oracle.graal.lir.sparc.SPARCMove.StoreConstantOp;
import com.oracle.graal.lir.sparc.SPARCMove.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;

public class SPARCHotSpotLIRGenerator extends SPARCLIRGenerator implements HotSpotLIRGenerator {

    final HotSpotVMConfig config;

    public SPARCHotSpotLIRGenerator(HotSpotProviders providers, HotSpotVMConfig config, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(providers, cc, lirGenRes);
        this.config = config;
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    /**
     * The slot reserved for storing the original return address when a frame is marked for
     * deoptimization. The return address slot in the callee is overwritten with the address of a
     * deoptimization stub.
     */
    private StackSlot deoptimizationRescueSlot;

    @Override
    public StackSlot getLockSlot(int lockDepth) {
        return ((HotSpotDebugInfoBuilder) getDebugInfoBuilder()).lockStack().makeLockSlot(lockDepth);
    }

    @Override
    protected boolean needOnlyOopMaps() {
        // Stubs only need oop maps
        return getStub() != null;
    }

    public Stub getStub() {
        return ((SPARCHotSpotLIRGenerationResult) getResult()).getStub();
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, DeoptimizingNode info, Value... args) {
        HotSpotForeignCallLinkage hotspotLinkage = (HotSpotForeignCallLinkage) linkage;
        Variable result;
        DeoptimizingNode deoptInfo = null;
        if (hotspotLinkage.canDeoptimize()) {
            deoptInfo = info;
            assert deoptInfo != null || getStub() != null;
        }

        if (hotspotLinkage.needsJavaFrameAnchor()) {
            HotSpotRegistersProvider registers = getProviders().getRegisters();
            Register thread = registers.getThreadRegister();
            Register stackPointer = registers.getStackPointerRegister();
            append(new SPARCHotSpotCRuntimeCallPrologueOp(config.threadLastJavaSpOffset(), thread, stackPointer));
            result = super.emitForeignCall(hotspotLinkage, deoptInfo, args);
            append(new SPARCHotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset(), config.threadLastJavaPcOffset(), config.threadJavaFrameAnchorFlagsOffset(), thread));
        } else {
            result = super.emitForeignCall(hotspotLinkage, deoptInfo, args);
        }

        return result;
    }

    @Override
    public void emitReturn(Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(input.getKind());
            emitMove(operand, input);
        }
        append(new SPARCHotSpotReturnOp(operand, getStub() != null));
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        // append(new AMD64TailcallOp(args, address));
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitUnwind(Value exception) {
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER);
        CallingConvention linkageCc = linkage.getOutgoingCallingConvention();
        assert linkageCc.getArgumentCount() == 2;
        RegisterValue exceptionParameter = (RegisterValue) linkageCc.getArgument(0);
        emitMove(exceptionParameter, exception);
        append(new SPARCHotSpotUnwindOp(exceptionParameter));
    }

    private void moveDeoptValuesToThread(Value actionAndReason, Value speculation) {
        moveValueToThread(actionAndReason, runtime().getConfig().pendingDeoptimizationOffset);
        moveValueToThread(speculation, runtime().getConfig().pendingFailedSpeculationOffset);
    }

    private void moveValueToThread(Value v, int offset) {
        Kind wordKind = getProviders().getCodeCache().getTarget().wordKind;
        RegisterValue thread = getProviders().getRegisters().getThreadRegister().asValue(wordKind);
        SPARCAddressValue pendingDeoptAddress = new SPARCAddressValue(v.getKind(), thread, offset);
        append(new StoreOp(v.getKind(), pendingDeoptAddress, emitMove(v), null));
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, DeoptimizingNode deopting) {
        moveDeoptValuesToThread(actionAndReason, speculation);
        append(new SPARCDeoptimizeOp(state(deopting)));
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        moveDeoptValuesToThread(getMetaAccess().encodeDeoptActionAndReason(action, reason, 0), Constant.NULL_OBJECT);
        append(new SPARCHotSpotDeoptimizeCallerOp());
    }

    private static boolean isCompressCandidate(Access access) {
        return access != null && access.isCompressible();
    }

    @Override
    public Variable emitLoad(PlatformKind kind, Value address, Access access) {
        SPARCAddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(kind);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        if (isCompressCandidate(access)) {
            if (config.useCompressedOops && kind == Kind.Object) {
                // append(new LoadCompressedPointer(kind, result, loadAddress, access != null ?
                // state(access) :
                // null, config.narrowOopBase, config.narrowOopShift,
                // config.logMinObjAlignment));
                throw GraalInternalError.unimplemented();
            } else if (config.useCompressedClassPointers && kind == Kind.Long) {
                // append(new LoadCompressedPointer(kind, result, loadAddress, access != null ?
                // state(access) :
                // null, config.narrowKlassBase, config.narrowKlassShift,
                // config.logKlassAlignment));
                throw GraalInternalError.unimplemented();
            } else {
                append(new LoadOp((Kind) kind, result, loadAddress, state));
            }
        } else {
            append(new LoadOp((Kind) kind, result, loadAddress, state));
        }
        return result;
    }

    @Override
    public void emitStore(PlatformKind kind, Value address, Value inputVal, Access access) {
        SPARCAddressValue storeAddress = asAddressValue(address);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        if (isConstant(inputVal)) {
            Constant c = asConstant(inputVal);
            if (canStoreConstant(c, isCompressCandidate(access))) {
                if (inputVal.getKind() == Kind.Object) {
                    append(new StoreConstantOp((Kind) kind, storeAddress, c, state, config.useCompressedOops && isCompressCandidate(access)));
                } else if (inputVal.getKind() == Kind.Long) {
                    append(new StoreConstantOp((Kind) kind, storeAddress, c, state, config.useCompressedClassPointers && isCompressCandidate(access)));
                } else {
                    append(new StoreConstantOp((Kind) kind, storeAddress, c, state, false));
                }
                return;
            }
        }
        Variable input = load(inputVal);
        if (isCompressCandidate(access)) {
            if (config.useCompressedOops && kind == Kind.Object) {
                // if (input.getKind() == Kind.Object) {
                // Variable scratch = newVariable(Kind.Long);
                // append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state,
                // config.narrowOopBase, config.narrowOopShift,
                // config.logMinObjAlignment));
                // } else {
                // // the input oop is already compressed
                // append(new StoreOp(input.getKind(), storeAddress, input, state));
                // }
                throw GraalInternalError.unimplemented();
            } else if (config.useCompressedClassPointers && kind == Kind.Long) {
                // Variable scratch = newVariable(Kind.Long);
                // append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state,
                // config.narrowKlassBase, config.narrowKlassShift,
                // config.logKlassAlignment));
                throw GraalInternalError.unimplemented();
            } else {
                append(new StoreOp((Kind) kind, storeAddress, input, state));
            }
        } else {
            append(new StoreOp((Kind) kind, storeAddress, input, state));
        }
    }

    public Value emitCompareAndSwap(Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        throw new InternalError("NYI");
    }

    @Override
    public Value emitNot(Value input) {
        GraalInternalError.shouldNotReachHere("binary negation not implemented");
        return null;
    }

    public StackSlot getDeoptimizationRescueSlot() {
        return deoptimizationRescueSlot;
    }

    public Value emitCompress(Value pointer, CompressEncoding encoding) {
        // TODO
        throw GraalInternalError.unimplemented();
    }

    public Value emitUncompress(Value pointer, CompressEncoding encoding) {
        // TODO
        throw GraalInternalError.unimplemented();
    }
}
