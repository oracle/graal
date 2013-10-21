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
import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.sparc.SPARC.*;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.sparc.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.lir.sparc.SPARCMove.CompareAndSwapOp;
import com.oracle.graal.lir.sparc.SPARCMove.LoadOp;
import com.oracle.graal.lir.sparc.SPARCMove.StoreConstantOp;
import com.oracle.graal.lir.sparc.SPARCMove.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

public class SPARCHotSpotLIRGenerator extends SPARCLIRGenerator implements HotSpotLIRGenerator {

    private final HotSpotVMConfig config;

    public SPARCHotSpotLIRGenerator(StructuredGraph graph, HotSpotProviders providers, HotSpotVMConfig config, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, providers, frameMap, cc, lir);
        this.config = config;
    }

    @Override
    protected HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    /**
     * The slot reserved for storing the original return address when a frame is marked for
     * deoptimization. The return address slot in the callee is overwritten with the address of a
     * deoptimization stub.
     */
    StackSlot deoptimizationRescueSlot;

    @SuppressWarnings("hiding")
    @Override
    protected DebugInfoBuilder createDebugInfoBuilder(NodeMap<Value> nodeOperands) {
        assert config.basicLockSize == 8;
        HotSpotLockStack lockStack = new HotSpotLockStack(frameMap, Kind.Long);
        return new HotSpotDebugInfoBuilder(nodeOperands, lockStack);
    }

    @Override
    public StackSlot getLockSlot(int lockDepth) {
        return ((HotSpotDebugInfoBuilder) debugInfoBuilder).lockStack().makeLockSlot(lockDepth);
    }

    @Override
    protected boolean needOnlyOopMaps() {
        // Stubs only need oop maps
        return graph.start() instanceof StubStartNode;
    }

    Stub getStub() {
        if (graph.start() instanceof StubStartNode) {
            return ((StubStartNode) graph.start()).getStub();
        }
        return null;
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, DeoptimizingNode info, Value... args) {
        Variable result;

        if (linkage.canDeoptimize()) {
            assert info != null;
            HotSpotRegistersProvider registers = getProviders().getRegisters();
            Register thread = registers.getThreadRegister();
            Register stackPointer = registers.getStackPointerRegister();
            append(new SPARCHotSpotCRuntimeCallPrologueOp(config.threadLastJavaSpOffset, thread, stackPointer));
            result = super.emitForeignCall(linkage, info, args);
            append(new SPARCHotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset, config.threadLastJavaPcOffset, config.threadJavaFrameAnchorFlagsOffset, thread));
        } else {
            result = super.emitForeignCall(linkage, null, args);
        }

        return result;
    }

    @Override
    protected void emitReturn(Value input) {
        append(new SPARCHotSpotReturnOp(input, getStub() != null));
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        LIRFrameState info = state(i);
        append(new SPARCHotSpotSafepointOp(info, config, this));
    }

    @Override
    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        Kind kind = x.newValue().kind();
        assert kind == x.expectedValue().kind();

        Variable address = load(operand(x.object()));
        Value offset = operand(x.offset());
        Variable cmpValue = (Variable) loadNonConst(operand(x.expectedValue()));
        Variable newValue = load(operand(x.newValue()));

        if (ValueUtil.isConstant(offset)) {
            assert !getCodeCache().needsDataPatch(asConstant(offset));
            Variable longAddress = newVariable(Kind.Long);
            emitMove(longAddress, address);
            address = emitAdd(longAddress, asConstant(offset));
        } else {
            if (isLegal(offset)) {
                address = emitAdd(address, offset);
            }
        }

        append(new CompareAndSwapOp(address, cmpValue, newValue));

        Variable result = newVariable(x.kind());
        emitMove(result, newValue);
        setResult(x, result);
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        // append(new AMD64TailcallOp(args, address));
        throw GraalInternalError.unimplemented();
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        InvokeKind invokeKind = ((HotSpotDirectCallTargetNode) callTarget).invokeKind();
        if (invokeKind == InvokeKind.Interface || invokeKind == InvokeKind.Virtual) {
            append(new SPARCHotspotDirectVirtualCallOp(callTarget.target(), result, parameters, temps, callState, invokeKind));
        } else {
            assert invokeKind == InvokeKind.Static || invokeKind == InvokeKind.Special;
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) callTarget.target();
            assert !Modifier.isAbstract(resolvedMethod.getModifiers()) : "Cannot make direct call to abstract method.";
            Constant metaspaceMethod = resolvedMethod.getMetaspaceMethodConstant();
            append(new SPARCHotspotDirectStaticCallOp(callTarget.target(), result, parameters, temps, callState, invokeKind, metaspaceMethod));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        AllocatableValue metaspaceMethod = g5.asValue();
        emitMove(metaspaceMethod, operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod()));
        AllocatableValue targetAddress = g3.asValue();
        emitMove(targetAddress, operand(callTarget.computedAddress()));
        append(new SPARCIndirectCallOp(callTarget.target(), result, parameters, temps, metaspaceMethod, targetAddress, callState));
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

    private void moveDeoptimizationActionAndReasonToThread(Value actionAndReason) {
        int pendingDeoptimizationOffset = runtime().getConfig().pendingDeoptimizationOffset;
        Kind wordKind = getProviders().getCodeCache().getTarget().wordKind;
        RegisterValue thread = getProviders().getRegisters().getThreadRegister().asValue(wordKind);
        SPARCAddressValue pendingDeoptAddress = new SPARCAddressValue(actionAndReason.getKind(), thread, pendingDeoptimizationOffset);
        append(new StoreOp(actionAndReason.getKind(), pendingDeoptAddress, emitMove(actionAndReason), null));
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, DeoptimizingNode deopting) {
        moveDeoptimizationActionAndReasonToThread(actionAndReason);
        append(new SPARCDeoptimizeOp(state(deopting)));
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        moveDeoptimizationActionAndReasonToThread(getMetaAccess().encodeDeoptActionAndReason(action, reason));
        append(new SPARCHotSpotDeoptimizeCallerOp());
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        append(new SPARCHotSpotPatchReturnAddressOp(load(operand(address))));
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        Variable handler = load(operand(handlerInCallerPc));
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(EXCEPTION_HANDLER_IN_CALLER);
        CallingConvention linkageCc = linkage.getOutgoingCallingConvention();
        assert linkageCc.getArgumentCount() == 2;
        RegisterValue exceptionFixed = (RegisterValue) linkageCc.getArgument(0);
        RegisterValue exceptionPcFixed = (RegisterValue) linkageCc.getArgument(1);
        emitMove(exceptionFixed, operand(exception));
        emitMove(exceptionPcFixed, operand(exceptionPc));
        Register thread = getProviders().getRegisters().getThreadRegister();
        SPARCHotSpotJumpToExceptionHandlerInCallerOp op = new SPARCHotSpotJumpToExceptionHandlerInCallerOp(handler, exceptionFixed, exceptionPcFixed, config.threadIsMethodHandleReturnOffset, thread);
        append(op);
    }

    private static boolean isCompressCandidate(DeoptimizingNode access) {
        return access != null && ((HeapAccess) access).isCompressible();
    }

    @Override
    public Variable emitLoad(Kind kind, Value address, DeoptimizingNode access) {
        SPARCAddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(kind);
        assert access == null || access instanceof HeapAccess;
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
                append(new LoadOp(kind, result, loadAddress, access != null ? state(access) : null));
            }
        } else {
            append(new LoadOp(kind, result, loadAddress, access != null ? state(access) : null));
        }
        return result;
    }

    @Override
    public void emitStore(Kind kind, Value address, Value inputVal, DeoptimizingNode access) {
        SPARCAddressValue storeAddress = asAddressValue(address);
        LIRFrameState state = access != null ? state(access) : null;
        if (isConstant(inputVal)) {
            Constant c = asConstant(inputVal);
            if (canStoreConstant(c)) {
                if (inputVal.getKind() == Kind.Object) {
                    append(new StoreConstantOp(kind, storeAddress, c, state, config.useCompressedOops && isCompressCandidate(access)));
                } else if (inputVal.getKind() == Kind.Long) {
                    append(new StoreConstantOp(kind, storeAddress, c, state, config.useCompressedClassPointers && isCompressCandidate(access)));
                } else {
                    append(new StoreConstantOp(kind, storeAddress, c, state, false));
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
                append(new StoreOp(kind, storeAddress, input, state));
            }
        } else {
            append(new StoreOp(kind, storeAddress, input, state));
        }
    }

    @Override
    public Value emitNot(Value input) {
        GraalInternalError.shouldNotReachHere("binary negation not implemented");
        return null;
    }
}
