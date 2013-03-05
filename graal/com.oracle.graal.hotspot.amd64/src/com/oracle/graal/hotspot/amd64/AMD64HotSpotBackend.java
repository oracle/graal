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
import static com.oracle.graal.api.code.CallingConvention.Type.*;
import static com.oracle.graal.api.code.ValueUtil.*;

import java.lang.reflect.*;

import sun.misc.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCallTarget.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;

/**
 * HotSpot AMD64 specific backend.
 */
public class AMD64HotSpotBackend extends HotSpotBackend {

    private static final Unsafe unsafe = Unsafe.getUnsafe();
    public static final Descriptor EXCEPTION_HANDLER = new Descriptor("exceptionHandler", true, void.class);
    public static final Descriptor DEOPT_HANDLER = new Descriptor("deoptHandler", true, void.class);
    public static final Descriptor IC_MISS_HANDLER = new Descriptor("icMissHandler", true, void.class);

    public AMD64HotSpotBackend(HotSpotRuntime runtime, TargetDescription target) {
        super(runtime, target);
    }

    @Override
    public LIRGenerator newLIRGenerator(StructuredGraph graph, FrameMap frameMap, ResolvedJavaMethod method, LIR lir) {
        return new HotSpotAMD64LIRGenerator(graph, runtime(), target, frameMap, method, lir);
    }

    static final class HotSpotAMD64LIRGenerator extends AMD64LIRGenerator implements HotSpotLIRGenerator {

        private HotSpotRuntime runtime() {
            return (HotSpotRuntime) runtime;
        }

        private HotSpotAMD64LIRGenerator(StructuredGraph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, ResolvedJavaMethod method, LIR lir) {
            super(graph, runtime, target, frameMap, method, lir);
        }

        @Override
        protected boolean needOnlyOopMaps() {
            // Stubs only need oop maps
            return runtime().asStub(method) != null;
        }

        @Override
        protected CallingConvention createCallingConvention() {
            Stub stub = runtime().asStub(method);
            if (stub != null) {
                return stub.getLinkage().getCallingConvention();
            }

            if (graph.getEntryBCI() == StructuredGraph.INVOCATION_ENTRY_BCI) {
                return super.createCallingConvention();
            } else {
                return frameMap.registerConfig.getCallingConvention(JavaCallee, method.getSignature().getReturnType(null), new JavaType[]{runtime.lookupJavaType(long.class)}, target, false);
            }
        }

        @Override
        public void visitSafepointNode(SafepointNode i) {
            LIRFrameState info = state();
            append(new AMD64SafepointOp(info, runtime().config, this));
        }

        @Override
        public void visitExceptionObject(ExceptionObjectNode x) {
            HotSpotVMConfig config = runtime().config;
            RegisterValue thread = runtime().threadRegister().asValue();
            Value exception = emitLoad(Kind.Object, thread, config.threadExceptionOopOffset, Value.ILLEGAL, 0, false);
            emitStore(Kind.Object, thread, config.threadExceptionOopOffset, Value.ILLEGAL, 0, Constant.NULL_OBJECT, false);
            emitStore(Kind.Long, thread, config.threadExceptionPcOffset, Value.ILLEGAL, 0, Constant.LONG_0, false);
            setResult(x, exception);
        }

        @SuppressWarnings("hiding")
        @Override
        public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
            Kind kind = x.newValue().kind();
            assert kind == x.expectedValue().kind();

            Value expected = loadNonConst(operand(x.expectedValue()));
            Variable newVal = load(operand(x.newValue()));

            int disp = 0;
            AMD64Address address;
            Value index = operand(x.offset());
            if (ValueUtil.isConstant(index) && NumUtil.isInt(ValueUtil.asConstant(index).asLong() + disp)) {
                assert !runtime.needsDataPatch(asConstant(index));
                disp += (int) ValueUtil.asConstant(index).asLong();
                address = new AMD64Address(kind, load(operand(x.object())), disp);
            } else {
                address = new AMD64Address(kind, load(operand(x.object())), load(index), AMD64Address.Scale.Times1, disp);
            }

            RegisterValue rax = AMD64.rax.asValue(kind);
            emitMove(rax, expected);
            append(new CompareAndSwapOp(rax, address, rax, newVal));

            Variable result = newVariable(x.kind());
            emitMove(result, rax);
            setResult(x, result);
        }

        @Override
        public void emitTailcall(Value[] args, Value address) {
            append(new AMD64TailcallOp(args, address));

        }

        @Override
        protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            append(new AMD64DirectCallOp(callTarget.target(), result, parameters, temps, callState, ((HotSpotDirectCallTargetNode) callTarget).invokeKind()));
        }

        @Override
        protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            Value metaspaceMethod = AMD64.rbx.asValue();
            emitMove(metaspaceMethod, operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod()));
            Value targetAddress = AMD64.rax.asValue();
            emitMove(targetAddress, operand(callTarget.computedAddress()));
            append(new AMD64IndirectCallOp(callTarget.target(), result, parameters, temps, metaspaceMethod, targetAddress, callState));
        }
    }

    /**
     * Emits code to do stack overflow checking.
     * 
     * @param afterFrameInit specifies if the stack pointer has already been adjusted to allocate
     *            the current frame
     */
    protected static void emitStackOverflowCheck(TargetMethodAssembler tasm, boolean afterFrameInit) {
        if (GraalOptions.StackShadowPages > 0) {

            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            int frameSize = tasm.frameMap.frameSize();
            if (frameSize > 0) {
                int lastFramePage = frameSize / unsafe.pageSize();
                // emit multiple stack bangs for methods with frames larger than a page
                for (int i = 0; i <= lastFramePage; i++) {
                    int disp = (i + GraalOptions.StackShadowPages) * unsafe.pageSize();
                    if (afterFrameInit) {
                        disp -= frameSize;
                    }
                    tasm.blockComment("[stack overflow check]");
                    asm.movq(new AMD64Address(asm.target.wordKind, AMD64.RSP, -disp), AMD64.rax);
                }
            }
        }
    }

    class HotSpotFrameContext implements FrameContext {

        @Override
        public void enter(TargetMethodAssembler tasm) {
            FrameMap frameMap = tasm.frameMap;
            int frameSize = frameMap.frameSize();

            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            emitStackOverflowCheck(tasm, false);
            asm.push(rbp);
            asm.movq(rbp, rsp);
            asm.decrementq(rsp, frameSize - 8); // account for the push of RBP above
            if (GraalOptions.ZapStackOnMethodEntry) {
                final int intSize = 4;
                for (int i = 0; i < frameSize / intSize; ++i) {
                    asm.movl(new AMD64Address(Kind.Int, rsp.asValue(), i * intSize), 0xC1C1C1C1);
                }
            }
            CalleeSaveLayout csl = frameMap.registerConfig.getCalleeSaveLayout();
            if (csl != null && csl.size != 0) {
                int frameToCSA = frameMap.offsetToCalleeSaveArea();
                assert frameToCSA >= 0;
                asm.save(csl, frameToCSA);
            }
        }

        @Override
        public void leave(TargetMethodAssembler tasm) {
            int frameSize = tasm.frameMap.frameSize();
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            CalleeSaveLayout csl = tasm.frameMap.registerConfig.getCalleeSaveLayout();

            if (csl != null && csl.size != 0) {
                tasm.compilationResult.setRegisterRestoreEpilogueOffset(asm.codeBuffer.position());
                // saved all registers, restore all registers
                int frameToCSA = tasm.frameMap.offsetToCalleeSaveArea();
                asm.restore(csl, frameToCSA);
            }

            asm.incrementq(rsp, frameSize - 8); // account for the pop of RBP below
            asm.pop(rbp);
        }
    }

    @Override
    public TargetMethodAssembler newAssembler(FrameMap frameMap, LIR lir) {
        // Omit the frame if the method:
        // - has no spill slots or other slots allocated during register allocation
        // - has no callee-saved registers
        // - has no incoming arguments passed on the stack
        // - has no instructions with debug info
        boolean omitFrame = GraalOptions.CanOmitFrame && frameMap.frameSize() == frameMap.initialFrameSize && frameMap.registerConfig.getCalleeSaveLayout().registers.length == 0 &&
                        !lir.hasArgInCallerFrame() && !lir.hasDebugInfo();

        AbstractAssembler masm = new AMD64MacroAssembler(target, frameMap.registerConfig);
        HotSpotFrameContext frameContext = omitFrame ? null : new HotSpotFrameContext();
        TargetMethodAssembler tasm = new TargetMethodAssembler(target, runtime(), frameMap, masm, frameContext, lir.stubs);
        tasm.setFrameSize(frameMap.frameSize());
        tasm.compilationResult.setCustomStackAreaOffset(frameMap.offsetToCustomArea());
        return tasm;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, ResolvedJavaMethod method, LIR lir) {
        AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
        FrameMap frameMap = tasm.frameMap;
        RegisterConfig regConfig = frameMap.registerConfig;
        HotSpotVMConfig config = runtime().config;
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        Label unverifiedStub = isStatic ? null : new Label();

        // Emit the prefix

        if (!isStatic) {
            tasm.recordMark(Marks.MARK_UNVERIFIED_ENTRY);
            CallingConvention cc = regConfig.getCallingConvention(JavaCallee, null, new JavaType[]{runtime().lookupJavaType(Object.class)}, target, false);
            Register inlineCacheKlass = rax; // see definition of IC_Klass in
                                             // c1_LIRAssembler_x86.cpp
            Register receiver = asRegister(cc.getArgument(0));
            AMD64Address src = new AMD64Address(target.wordKind, receiver.asValue(), config.hubOffset);

            asm.cmpq(inlineCacheKlass, src);
            asm.jcc(ConditionFlag.NotEqual, unverifiedStub);
        }

        asm.align(config.codeEntryAlignment);
        tasm.recordMark(Marks.MARK_OSR_ENTRY);
        tasm.recordMark(Marks.MARK_VERIFIED_ENTRY);

        // Emit code for the LIR
        lir.emitCode(tasm);

        boolean frameOmitted = tasm.frameContext == null;
        if (!frameOmitted) {
            tasm.recordMark(Marks.MARK_EXCEPTION_HANDLER_ENTRY);
            AMD64Call.directCall(tasm, asm, runtime().lookupRuntimeCall(EXCEPTION_HANDLER), null);

            tasm.recordMark(Marks.MARK_DEOPT_HANDLER_ENTRY);
            AMD64Call.directCall(tasm, asm, runtime().lookupRuntimeCall(DEOPT_HANDLER), null);
        } else {
            // No need to emit the stubs for entries back into the method since
            // it has no calls that can cause such "return" entries
            assert !frameMap.accessesCallerFrame();
        }

        if (unverifiedStub != null) {
            asm.bind(unverifiedStub);
            AMD64Call.directJmp(tasm, asm, runtime().lookupRuntimeCall(IC_MISS_HANDLER));
        }

        for (int i = 0; i < GraalOptions.MethodEndBreakpointGuards; ++i) {
            asm.int3();
        }

    }

}
