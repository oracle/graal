/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.hsail.*;
import com.oracle.graal.lir.hsail.HSAILControlFlow.CondMoveOp;
import com.oracle.graal.lir.hsail.HSAILControlFlow.DeoptimizeOp;
import com.oracle.graal.lir.hsail.HSAILControlFlow.ForeignCall1ArgOp;
import com.oracle.graal.lir.hsail.HSAILControlFlow.ForeignCall2ArgOp;
import com.oracle.graal.lir.hsail.HSAILControlFlow.ForeignCallNoArgOp;
import com.oracle.graal.lir.hsail.HSAILMove.CompareAndSwapOp;
import com.oracle.graal.lir.hsail.HSAILMove.LoadCompressedPointer;
import com.oracle.graal.lir.hsail.HSAILMove.LoadOp;
import com.oracle.graal.lir.hsail.HSAILMove.MoveFromRegOp;
import com.oracle.graal.lir.hsail.HSAILMove.MoveToRegOp;
import com.oracle.graal.lir.hsail.HSAILMove.StoreCompressedPointer;
import com.oracle.graal.lir.hsail.HSAILMove.StoreConstantOp;
import com.oracle.graal.lir.hsail.HSAILMove.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.util.*;

/**
 * The HotSpot specific portion of the HSAIL LIR generator.
 */
public class HSAILHotSpotLIRGenerator extends HSAILLIRGenerator implements HotSpotLIRGenerator {

    final HotSpotVMConfig config;

    public HSAILHotSpotLIRGenerator(Providers providers, HotSpotVMConfig config, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(providers, cc, lirGenRes);
        this.config = config;
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    int getLogMinObjectAlignment() {
        return config.logMinObjAlignment();
    }

    int getNarrowOopShift() {
        return config.narrowOopShift;
    }

    long getNarrowOopBase() {
        return config.narrowOopBase;
    }

    int getLogKlassAlignment() {
        return config.logKlassAlignment;
    }

    int getNarrowKlassShift() {
        return config.narrowKlassShift;
    }

    long getNarrowKlassBase() {
        return config.narrowKlassBase;
    }

    private static boolean isCompressCandidate(Access access) {
        return access != null && access.isCompressible();
    }

    @Override
    public boolean canStoreConstant(Constant c, boolean isCompressed) {
        return true;
    }

    /**
     * Returns whether or not the input access should be (de)compressed.
     */
    private boolean isCompressedOperation(PlatformKind kind, Access access) {
        return access != null && access.isCompressible() && ((kind == Kind.Long && config.useCompressedClassPointers) || (kind == Kind.Object && config.useCompressedOops));
    }

    private static Kind getMemoryKind(PlatformKind kind) {
        if (kind == NarrowOopStamp.NarrowOop) {
            return Kind.Int;
        } else {
            return (Kind) kind;
        }
    }

    @Override
    public Variable emitLoad(PlatformKind kind, Value address, Access access) {
        HSAILAddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(kind);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        if (isCompressCandidate(access) && config.useCompressedOops && kind == Kind.Object) {
            Variable scratch = newVariable(Kind.Long);
            append(new LoadCompressedPointer(Kind.Object, result, scratch, loadAddress, state, getNarrowOopBase(), getNarrowOopShift(), getLogMinObjectAlignment()));
        } else if (isCompressCandidate(access) && config.useCompressedClassPointers && kind == Kind.Long) {
            Variable scratch = newVariable(Kind.Long);
            append(new LoadCompressedPointer(Kind.Object, result, scratch, loadAddress, state, getNarrowKlassBase(), getNarrowKlassShift(), getLogKlassAlignment()));
        } else if (kind == NarrowOopStamp.NarrowOop) {
            append(new LoadOp(Kind.Int, result, loadAddress, state));
        } else {
            append(new LoadOp(getMemoryKind(kind), result, loadAddress, state));
        }
        return result;
    }

    @Override
    public void emitStore(PlatformKind kind, Value address, Value inputVal, Access access) {
        HSAILAddressValue storeAddress = asAddressValue(address);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        boolean isCompressed = isCompressedOperation(kind, access);
        if (isConstant(inputVal)) {
            Constant c = asConstant(inputVal);
            if (canStoreConstant(c, isCompressed)) {
                if (isCompressed) {
                    if ((c.getKind() == Kind.Object) && c.isNull()) {
                        // Constant value = c.isNull() ? c : compress(c, config.getOopEncoding());
                        append(new StoreConstantOp(Kind.Int, storeAddress, Constant.forInt(0), state));
                    } else if (c.getKind() == Kind.Long) {
                        // It's always a good idea to directly store compressed constants since they
                        // have to be materialized as 64 bits encoded otherwise.
                        Constant value = compress(c, config.getKlassEncoding());
                        append(new StoreConstantOp(Kind.Int, storeAddress, value, state));
                    } else {
                        throw GraalInternalError.shouldNotReachHere("can't handle: " + access);
                    }
                    return;
                } else {
                    append(new StoreConstantOp(getMemoryKind(kind), storeAddress, c, state));
                    return;
                }
            }
        }
        Variable input = load(inputVal);
        if (isCompressCandidate(access) && config.useCompressedOops && kind == Kind.Object) {
            Variable scratch = newVariable(Kind.Long);
            append(new StoreCompressedPointer(Kind.Object, storeAddress, input, scratch, state, getNarrowOopBase(), getNarrowOopShift(), getLogMinObjectAlignment()));
        } else if (isCompressCandidate(access) && config.useCompressedClassPointers && kind == Kind.Long) {
            Variable scratch = newVariable(Kind.Long);
            append(new StoreCompressedPointer(Kind.Object, storeAddress, input, scratch, state, getNarrowKlassBase(), getNarrowKlassShift(), getLogKlassAlignment()));
        } else if (kind == NarrowOopStamp.NarrowOop) {
            append(new StoreOp(Kind.Int, storeAddress, input, state));
        } else {
            append(new StoreOp(getMemoryKind(kind), storeAddress, input, state));
        }
    }

    public Value emitCompareAndSwap(Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        PlatformKind kind = newValue.getPlatformKind();
        assert kind == expectedValue.getPlatformKind();
        Kind memKind = getMemoryKind(kind);

        HSAILAddressValue addressValue = asAddressValue(address);
        Variable expected = emitMove(expectedValue);
        Variable casResult = newVariable(kind);
        append(new CompareAndSwapOp(memKind, casResult, addressValue, expected, asAllocatable(newValue)));

        assert trueValue.getPlatformKind() == falseValue.getPlatformKind();
        Variable nodeResult = newVariable(trueValue.getPlatformKind());
        append(new CondMoveOp(HSAILLIRGenerator.mapKindToCompareOp(memKind), casResult, expected, nodeResult, Condition.EQ, trueValue, falseValue));
        return nodeResult;
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, DeoptimizingNode deopting) {
        emitDeoptimizeInner(actionAndReason, state(deopting), "emitDeoptimize");
    }

    /***
     * We need 64-bit and 32-bit scratch registers for the codegen $s0 can be live at this block.
     */
    private void emitDeoptimizeInner(Value actionAndReason, LIRFrameState lirFrameState, String emitName) {
        DeoptimizeOp deopt = new DeoptimizeOp(actionAndReason, lirFrameState, emitName, getMetaAccess());
        ((HSAILHotSpotLIRGenerationResult) getResult()).addDeopt(deopt);
        append(deopt);
    }

    /***
     * This is a very temporary solution to emitForeignCall. We don't really support foreign calls
     * yet, but we do want to generate dummy code for them. The ForeignCallXXXOps just end up
     * emitting a comment as to what Foreign call they would have made.
     */
    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, DeoptimizingNode info, Value... args) {
        Variable result = newVariable(Kind.Object);  // linkage.getDescriptor().getResultType());

        // to make the LIRVerifier happy, we move any constants into registers
        Value[] argLocations = new Value[args.length];
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = newVariable(arg.getKind());
            emitMove(loc, arg);
            argLocations[i] = loc;
        }

        // here we could check the callName if we wanted to only handle certain callnames
        String callName = linkage.getDescriptor().getName();
        switch (argLocations.length) {
            case 0:
                append(new ForeignCallNoArgOp(callName, result));
                break;
            case 1:
                append(new ForeignCall1ArgOp(callName, result, argLocations[0]));
                break;
            case 2:
                append(new ForeignCall2ArgOp(callName, result, argLocations[0], argLocations[1]));
                break;
            default:
                throw new InternalError("NYI emitForeignCall " + callName + ", " + argLocations.length + ", " + linkage);
        }
        return result;
    }

    @Override
    protected HSAILLIRInstruction createMove(AllocatableValue dst, Value src) {
        if (dst.getPlatformKind() == NarrowOopStamp.NarrowOop) {
            if (isRegister(src) || isStackSlot(dst)) {
                return new MoveFromRegOp(Kind.Int, dst, src);
            } else {
                return new MoveToRegOp(Kind.Int, dst, src);
            }
        } else {
            return super.createMove(dst, src);
        }
    }

    @Override
    protected void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        // this version of emitForeignCall not used for now
    }

    /**
     * @return a compressed version of the incoming constant lifted from AMD64HotSpotLIRGenerator
     */
    protected static Constant compress(Constant c, CompressEncoding encoding) {
        if (c.getKind() == Kind.Long) {
            int compressedValue = (int) (((c.asLong() - encoding.base) >> encoding.shift) & 0xffffffffL);
            if (c instanceof HotSpotMetaspaceConstant) {
                return HotSpotMetaspaceConstant.forMetaspaceObject(Kind.Int, compressedValue, HotSpotMetaspaceConstant.getMetaspaceObject(c));
            } else {
                return Constant.forIntegerKind(Kind.Int, compressedValue);
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public void emitTailcall(Value[] args, Value address) {
        throw GraalInternalError.unimplemented();
    }

    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        throw GraalInternalError.unimplemented();
    }

    public StackSlot getLockSlot(int lockDepth) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        Variable result = newVariable(NarrowOopStamp.NarrowOop);
        append(new HSAILMove.CompressPointer(result, newVariable(pointer.getPlatformKind()), asAllocatable(pointer), encoding.base, encoding.shift, encoding.alignment, nonNull));
        return result;
    }

    @Override
    public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        Variable result = newVariable(Kind.Object);
        append(new HSAILMove.UncompressPointer(result, asAllocatable(pointer), encoding.base, encoding.shift, encoding.alignment, nonNull));
        return result;
    }

    public void emitLeaveCurrentStackFrame() {
        throw GraalInternalError.unimplemented();
    }

    public void emitLeaveDeoptimizedStackFrame(Value frameSize, Value initialInfo) {
        throw GraalInternalError.unimplemented();
    }

    public void emitEnterUnpackFramesStackFrame(Value framePc, Value senderSp, Value senderFp) {
        throw GraalInternalError.unimplemented();
    }

    public void emitLeaveUnpackFramesStackFrame() {
        throw GraalInternalError.unimplemented();
    }

    public SaveRegistersOp emitSaveAllRegisters() {
        throw GraalInternalError.unimplemented();
    }

    public void emitPushInterpreterFrame(Value frameSize, Value framePc, Value senderSp, Value initialInfo) {
        throw GraalInternalError.unimplemented();
    }

    public Value emitUncommonTrapCall(Value trapRequest, SaveRegistersOp saveRegisterOp) {
        throw GraalInternalError.unimplemented();
    }
}
