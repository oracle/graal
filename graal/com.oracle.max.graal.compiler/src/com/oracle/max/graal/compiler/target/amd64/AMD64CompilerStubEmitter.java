/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.target.amd64;

import static com.sun.cri.ci.CiCallingConvention.Type.*;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.stub.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiRegister.RegisterFlag;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;
import com.sun.cri.xir.CiXirAssembler.XirConstant;
import com.sun.cri.xir.CiXirAssembler.XirConstantOperand;
import com.sun.cri.xir.CiXirAssembler.XirOperand;
import com.sun.cri.xir.CiXirAssembler.XirParameter;
import com.sun.cri.xir.CiXirAssembler.XirRegister;
import com.sun.cri.xir.CiXirAssembler.XirTemp;

/**
 * An object used to produce a single compiler stub.
 */
public class AMD64CompilerStubEmitter {

    private static final CiRegister convertArgument = AMD64.xmm0;
    private static final CiRegister convertResult = AMD64.rax;

    /**
     * The slots in which the stub finds its incoming arguments.
     * To get the arguments from the perspective of the stub's caller,
     * use {@link CiStackSlot#asOutArg()}.
     */
    private final CiStackSlot[] inArgs;

    /**
     * The slot in which the stub places its return value (if any).
     * To get the value from the perspective of the stub's caller,
     * use {@link CiStackSlot#asOutArg()}.
     */
    private final CiStackSlot outResult;

    /**
     * The layout of the callee save area of the stub being emitted.
     */
    private CiCalleeSaveLayout csl;

    /**
     * The compilation object for the stub being emitted.
     */
    private final GraalCompilation comp;

    private final GraalContext context;

    private final TargetMethodAssembler tasm;
    private final AMD64MacroAssembler asm;

    public AMD64CompilerStubEmitter(GraalContext context, GraalCompilation compilation, CiKind[] argTypes, CiKind resultKind) {
        compilation.initFrameMap(0);
        this.comp = compilation;
        this.context = context;
        final RiRegisterConfig registerConfig = compilation.compiler.compilerStubRegisterConfig;
        this.asm = new AMD64MacroAssembler(compilation.compiler.target, registerConfig);
        this.tasm = new TargetMethodAssembler(compilation, asm);

        inArgs = new CiStackSlot[argTypes.length];
        if (argTypes.length != 0) {
            final CiValue[] locations = registerConfig.getCallingConvention(JavaCallee, argTypes, compilation.compiler.target, true).locations;
            for (int i = 0; i < argTypes.length; i++) {
                inArgs[i] = (CiStackSlot) locations[i];
            }
        }

        if (resultKind != CiKind.Void) {
            final CiValue location = registerConfig.getCallingConvention(JavaCallee, new CiKind[] {resultKind}, compilation.compiler.target, true).locations[0];
            outResult = (CiStackSlot) location;
        } else {
            outResult = null;
        }
    }

    public CompilerStub emit(CiRuntimeCall runtimeCall) {
        emitStandardForward(null, runtimeCall);
        String name = "graal-stub-" + runtimeCall;
        CiTargetMethod targetMethod = tasm.finishTargetMethod(name, comp.compiler.runtime, true);
        Object stubObject = comp.compiler.runtime.registerCompilerStub(targetMethod, name);
        return new CompilerStub(null, runtimeCall.resultKind, stubObject, inArgs, outResult);
    }

    public CompilerStub emit(CompilerStub.Id stub) {
        switch (stub) {
            case f2i:
                emitF2I();
                break;
            case f2l:
                emitF2L();
                break;
            case d2i:
                emitD2I();
                break;
            case d2l:
                emitD2L();
                break;
        }

        String name = "graal-stub-" + stub;
        CiTargetMethod targetMethod = tasm.finishTargetMethod(name, comp.compiler.runtime, true);
        Object stubObject = comp.compiler.runtime.registerCompilerStub(targetMethod, name);
        return new CompilerStub(stub, stub.resultKind, stubObject, inArgs, outResult);
    }

    private CiValue allocateOperand(XirTemp temp, ArrayList<CiRegister> allocatableRegisters) {
        if (temp instanceof XirRegister) {
            XirRegister fixed = (XirRegister) temp;
            return fixed.register;
        }

        return newRegister(temp.kind, allocatableRegisters);
    }

    private CiValue newRegister(CiKind kind, ArrayList<CiRegister> allocatableRegisters) {
        assert kind != CiKind.Float && kind != CiKind.Double;
        assert allocatableRegisters.size() > 0;
        return allocatableRegisters.remove(allocatableRegisters.size() - 1).asValue(kind);
    }

    public CompilerStub emit(XirTemplate template) {
        ArrayList<CiRegister> allocatableRegisters = new ArrayList<CiRegister>(Arrays.asList(comp.registerConfig.getCategorizedAllocatableRegisters().get(RegisterFlag.CPU)));
        for (XirTemp t : template.temps) {
            if (t instanceof XirRegister) {
                final XirRegister fixed = (XirRegister) t;
                if (fixed.register.isRegister()) {
                    allocatableRegisters.remove(fixed.register.asRegister());
                }
            }
        }

        prologue(comp.registerConfig.getCalleeSaveLayout());

        CiValue[] operands = new CiValue[template.variableCount];

        XirOperand resultOperand = template.resultOperand;

        if (template.allocateResultOperand) {
            CiValue outputOperand = CiValue.IllegalValue;
            // This snippet has a result that must be separately allocated
            // Otherwise it is assumed that the result is part of the inputs
            if (resultOperand.kind != CiKind.Void && resultOperand.kind != CiKind.Illegal) {
                outputOperand = outResult;
                assert operands[resultOperand.index] == null;
            }
            operands[resultOperand.index] = outputOperand;
        }

        for (int i = 0; i < template.parameters.length; i++) {
            final XirParameter param = template.parameters[i];
            assert !(param instanceof XirConstantOperand) : "constant parameters not supported for stubs";

            CiValue op = inArgs[i];
            assert operands[param.index] == null;

            // Is the value destroyed?
            if (template.isParameterDestroyed(param.parameterIndex)) {
                CiValue newOp = newRegister(op.kind, allocatableRegisters);
                AMD64MoveOpcode.move(tasm, asm, newOp, op);
                operands[param.index] = newOp;
            } else {
                operands[param.index] = op;
            }
        }

        for (XirConstant c : template.constants) {
            assert operands[c.index] == null;
            operands[c.index] = c.value;
        }

        for (XirTemp t : template.temps) {
            CiValue op = allocateOperand(t, allocatableRegisters);
            assert operands[t.index] == null;
            operands[t.index] = op;
        }

        for (CiValue operand : operands) {
            assert operand != null;
        }

        Label[] labels = new Label[template.labels.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = new Label();
        }

        assert template.marks.length == 0 : "marks not supported in compiler stubs";
        AMD64XirOpcode.emitXirInstructions(tasm, asm, null, template.fastPath, labels, operands, null);
        epilogue();
        String stubName = "graal-" + template.name;
        CiTargetMethod targetMethod = tasm.finishTargetMethod(stubName, comp.compiler.runtime, true);
        Object stubObject = comp.compiler.runtime.registerCompilerStub(targetMethod, stubName);
        return new CompilerStub(null, template.resultOperand.kind, stubObject, inArgs, outResult);
    }

    private CiKind[] getArgumentKinds(XirTemplate template) {
        CiXirAssembler.XirParameter[] params = template.parameters;
        CiKind[] result = new CiKind[params.length];
        for (int i = 0; i < params.length; i++) {
            result[i] = params[i].kind;
        }
        return result;
    }

    private void convertPrologue() {
        prologue(new CiCalleeSaveLayout(0, -1, comp.compiler.target.wordSize, convertArgument, convertResult));
        asm.movq(convertArgument, comp.frameMap().toStackAddress(inArgs[0]));
    }

    private void convertEpilogue() {
        asm.movq(comp.frameMap().toStackAddress(outResult), convertResult);
        epilogue();
    }

    private void emitD2L() {
        emitCOMISSD(true, false);
    }

    private void emitD2I() {
        emitCOMISSD(true, true);
    }

    private void emitF2L() {
        emitCOMISSD(false, false);
    }

    private void emitF2I() {
        emitCOMISSD(false, true);
    }

    private void emitCOMISSD(boolean isDouble, boolean isInt) {
        convertPrologue();
        if (isDouble) {
            asm.ucomisd(convertArgument, tasm.asDoubleConstRef(CiConstant.DOUBLE_0));
        } else {
            asm.ucomiss(convertArgument, tasm.asFloatConstRef(CiConstant.FLOAT_0));
        }
        Label nan = new Label();
        Label ret = new Label();
        asm.jccb(ConditionFlag.parity, nan);
        asm.jccb(ConditionFlag.below, ret);

        // input is > 0 -> return maxInt
        // result register already contains 0x80000000, so subtracting 1 gives 0x7fffffff
        asm.decrementl(convertResult, 1);
        asm.jmpb(ret);

        // input is NaN -> return 0
        asm.bind(nan);
        asm.xorptr(convertResult, convertResult);

        asm.bind(ret);
        convertEpilogue();
    }

    private void emitStandardForward(CompilerStub.Id stub, CiRuntimeCall call) {
        if (stub != null) {
            assert stub.resultKind == call.resultKind;
            assert stub.arguments.length == call.arguments.length;
            for (int i = 0; i < stub.arguments.length; i++) {
                assert stub.arguments[i] == call.arguments[i];
            }
        }

        prologue(comp.registerConfig.getCalleeSaveLayout());
        forwardRuntimeCall(call);
        epilogue();
    }

    private void prologue(CiCalleeSaveLayout csl) {
        assert this.csl == null;
        assert csl != null : "stub should define a callee save area";
        this.csl = csl;
        int entryCodeOffset = comp.compiler.runtime.codeOffset();
        if (entryCodeOffset != 0) {
            // pad to normal code entry point
            asm.nop(entryCodeOffset);
        }
        final int frameSize = frameSize();
        asm.subq(AMD64.rsp, frameSize);
        tasm.setFrameSize(frameSize);
        comp.frameMap().setFrameSize(frameSize);
        asm.save(csl, csl.frameOffsetToCSA);
    }

    private void epilogue() {
        tasm.targetMethod.setRegisterRestoreEpilogueOffset(asm.codeBuffer.position());

        // Restore registers
        int frameToCSA = csl.frameOffsetToCSA;
        asm.restore(csl, frameToCSA);

        // Restore rsp
        asm.addq(AMD64.rsp, frameSize());
        asm.ret(0);
    }

    private int frameSize() {
        return comp.compiler.target.alignFrameSize(csl.size);
    }

    private void forwardRuntimeCall(CiRuntimeCall call) {
        // Load arguments
        CiCallingConvention cc = comp.registerConfig.getCallingConvention(RuntimeCall, call.arguments, comp.compiler.target, false);
        for (int i = 0; i < cc.locations.length; ++i) {
            CiValue location = cc.locations[i];
            asm.movq(location.asRegister(), comp.frameMap().toStackAddress(inArgs[i]));
        }

        if (GraalOptions.AlignCallsForPatching) {
            asm.alignForPatchableDirectCall();
        }
        // Call to the runtime
        int before = asm.codeBuffer.position();
        asm.call();
        int after = asm.codeBuffer.position();
        tasm.recordDirectCall(before, after - before, comp.compiler.runtime.asCallTarget(call), null);
        asm.ensureUniquePC();

        if (call.resultKind != CiKind.Void) {
            CiRegister returnRegister = comp.registerConfig.getReturnRegister(call.resultKind);
            asm.movq(comp.frameMap().toStackAddress(outResult), returnRegister);
        }
    }
}
