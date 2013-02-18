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
package com.oracle.graal.compiler.ptx;

import static com.oracle.graal.api.code.CallingConvention.Type.JavaCallee;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.compiler.gen.LIRGenerator;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.hotspot.meta.HotSpotRuntime;
import com.oracle.graal.hotspot.nodes.DirectCompareAndSwapNode;
import com.oracle.graal.hotspot.stubs.Stub;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.GraalOptions;

/**
 * PTX specific backend.
 */
public class PTXBackend extends Backend {

    public PTXBackend(CodeCacheProvider runtime, TargetDescription target) {
        super(runtime, target);
    }

    public LIRGenerator newLIRGenerator(StructuredGraph graph, FrameMap frameMap, ResolvedJavaMethod method, LIR lir) {
        return new HotSpotPTXLIRGenerator(graph, runtime(), target, frameMap, method, lir);
    }

    static final class HotSpotPTXLIRGenerator extends PTXLIRGenerator implements HotSpotLIRGenerator {

        private HotSpotRuntime runtime() {
            return (HotSpotRuntime) runtime;
        }

        private HotSpotPTXLIRGenerator(StructuredGraph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, ResolvedJavaMethod method, LIR lir) {
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
            // We don't need safepoints in GPU code.
        }

        @Override
        public void visitExceptionObject(ExceptionObjectNode x) {
//            HotSpotVMConfig config = runtime().config;
//            RegisterValue thread = runtime().threadRegister().asValue();
//            Address exceptionAddress = new Address(Kind.Object, thread, config.threadExceptionOopOffset);
//            Address pcAddress = new Address(Kind.Long, thread, config.threadExceptionPcOffset);
//            Value exception = emitLoad(exceptionAddress, false);
//            emitStore(exceptionAddress, Constant.NULL_OBJECT, false);
//            emitStore(pcAddress, Constant.LONG_0, false);
//            setResult(x, exception);
            throw new InternalError("NYI");
        }

        @SuppressWarnings("hiding")
        @Override
        public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
//            Kind kind = x.newValue().kind();
//            assert kind == x.expectedValue().kind();
//
//            Value expected = loadNonConst(operand(x.expectedValue()));
//            Variable newVal = load(operand(x.newValue()));
//
//            int disp = 0;
//            Address address;
//            Value index = operand(x.offset());
//            if (ValueUtil.isConstant(index) && NumUtil.isInt(ValueUtil.asConstant(index).asLong() + disp)) {
//                assert !runtime.needsDataPatch(asConstant(index));
//                disp += (int) ValueUtil.asConstant(index).asLong();
//                address = new Address(kind, load(operand(x.object())), disp);
//            } else {
//                address = new Address(kind, load(operand(x.object())), load(index), Address.Scale.Times1, disp);
//            }
//
//            RegisterValue rax = AMD64.rax.asValue(kind);
//            emitMove(expected, rax);
//            append(new CompareAndSwapOp(rax, address, rax, newVal));
//
//            Variable result = newVariable(x.kind());
//            emitMove(rax, result);
//            setResult(x, result);
            throw new InternalError("NYI");
        }

        @Override
        public void emitTailcall(Value[] args, Value address) {
            //append(new AMD64TailcallOp(args, address));
            throw new InternalError("NYI");
        }

        @Override
        protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
//            append(new AMD64DirectCallOp(callTarget.target(), result, parameters, temps, callState, ((HotSpotDirectCallTargetNode) callTarget).invokeKind(), lir));
            throw new InternalError("NYI");
        }

        @Override
        protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
//            Value metaspaceMethod = AMD64.rbx.asValue();
//            emitMove(operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod()), metaspaceMethod);
//            Value targetAddress = AMD64.rax.asValue();
//            emitMove(operand(callTarget.computedAddress()), targetAddress);
//            append(new AMD64IndirectCallOp(callTarget.target(), result, parameters, temps, metaspaceMethod, targetAddress, callState));
            throw new InternalError("NYI");
        }
    }

    class HotSpotFrameContext implements FrameContext {

        @Override
        public void enter(TargetMethodAssembler tasm) {
            FrameMap frameMap = tasm.frameMap;
            int frameSize = frameMap.frameSize();

            PTXMacroAssembler asm = (PTXMacroAssembler) tasm.asm;
//            emitStackOverflowCheck(tasm, false);
//            asm.push(rbp);
//            asm.movq(rbp, rsp);
//            asm.decrementq(rsp, frameSize - 8); // account for the push of RBP above
//            if (GraalOptions.ZapStackOnMethodEntry) {
//                final int intSize = 4;
//                for (int i = 0; i < frameSize / intSize; ++i) {
//                    asm.movl(new Address(Kind.Int, rsp.asValue(), i * intSize), 0xC1C1C1C1);
//                }
//            }
//            CalleeSaveLayout csl = frameMap.registerConfig.getCalleeSaveLayout();
//            if (csl != null && csl.size != 0) {
//                int frameToCSA = frameMap.offsetToCalleeSaveArea();
//                assert frameToCSA >= 0;
//                asm.save(csl, frameToCSA);
//            }
        }

        @Override
        public void leave(TargetMethodAssembler tasm) {
            int frameSize = tasm.frameMap.frameSize();
            PTXMacroAssembler asm = (PTXMacroAssembler) tasm.asm;
            CalleeSaveLayout csl = tasm.frameMap.registerConfig.getCalleeSaveLayout();
            RegisterConfig regConfig = tasm.frameMap.registerConfig;

//            if (csl != null && csl.size != 0) {
//                tasm.targetMethod.setRegisterRestoreEpilogueOffset(asm.codeBuffer.position());
//                // saved all registers, restore all registers
//                int frameToCSA = tasm.frameMap.offsetToCalleeSaveArea();
//                asm.restore(csl, frameToCSA);
//            }
//
//            asm.incrementq(rsp, frameSize - 8); // account for the pop of RBP below
//            asm.pop(rbp);
//
//            if (GraalOptions.GenSafepoints) {
//                HotSpotVMConfig config = runtime().config;
//
//                // If at the return point, then the frame has already been popped
//                // so deoptimization cannot be performed here. The HotSpot runtime
//                // detects this case - see the definition of frame::should_be_deoptimized()
//
//                Register scratch = regConfig.getScratchRegister();
//                int offset = SafepointPollOffset % target.pageSize;
//                if (config.isPollingPageFar) {
//                    asm.movq(scratch, config.safepointPollingAddress + offset);
//                    tasm.recordMark(Marks.MARK_POLL_RETURN_FAR);
//                    asm.movq(scratch, new Address(tasm.target.wordKind, scratch.asValue()));
//                } else {
//                    tasm.recordMark(Marks.MARK_POLL_RETURN_NEAR);
//                    // The C++ code transforms the polling page offset into an RIP displacement
//                    // to the real address at that offset in the polling page.
//                    asm.movq(scratch, new Address(tasm.target.wordKind, rip.asValue(), offset));
//                }
//            }
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

        AbstractAssembler masm = new PTXMacroAssembler(target, frameMap.registerConfig);
        HotSpotFrameContext frameContext = omitFrame ? null : new HotSpotFrameContext();
        TargetMethodAssembler tasm = new TargetMethodAssembler(target, runtime(), frameMap, masm, frameContext, lir.stubs);
        tasm.setFrameSize(frameMap.frameSize());
        tasm.targetMethod.setCustomStackAreaOffset(frameMap.offsetToCustomArea());
        return tasm;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, ResolvedJavaMethod method, LIR lir) {
        // Emit the prologue
        final String name = method.getName();
        Buffer codeBuffer = tasm.asm.codeBuffer;
        
        codeBuffer.emitString(".version 1.4");
        codeBuffer.emitString(".target sm_10");
        //codeBuffer.emitString(".address_size 32");  // PTX ISA version 2.3
        codeBuffer.emitString0(".entry " + name + " (");
        codeBuffer.emitString("");
        
        Signature signature = method.getSignature();
        for (int i = 0; i < signature.getParameterCount(false); i++) {
            System.err.println(i+": "+signature.getParameterKind(i));
            String param = ".param .u32 param" + i; 
            codeBuffer.emitString(param);
        }
        
        codeBuffer.emitString0(") {");
        codeBuffer.emitString("");
        
        // XXX For now declare one predicate and all registers
        codeBuffer.emitString("  .reg .pred %p;");
        codeBuffer.emitString("  .reg .u32 %r<16>;");
        
        // Emit code for the LIR
        lir.emitCode(tasm);

        // Emit the epilogue
        codeBuffer.emitString0("}");
        codeBuffer.emitString("");
        
        byte[] data = codeBuffer.copyData(0, codeBuffer.position());
        System.err.println(new String(data));
    }
}
