package com.oracle.graal.lir.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.CompilationResult.RawData;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Nop;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.sparc.*;

@Opcode("CONST_DATA")
public class SPARCConstDataOp extends SPARCLIRInstruction {

    @Def({REG}) private AllocatableValue dst;
    private byte[] val;

    public SPARCConstDataOp(AllocatableValue dst, byte[] val) {
        this.dst = dst;
        this.val = val;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        RawData rawData = new RawData(val, 16);
        // recordDataReferenceInCoe will fix up PC relative. Therefore
        // the PC will be added later on to gather an absolute address
        crb.recordDataReferenceInCode(rawData);
        Register dstReg = ValueUtil.asLongReg(dst);
        new SPARCAssembler.Sethi(0, dstReg).emit(masm);

        Nop nop = new SPARCMacroAssembler.Nop();
        nop.emit(masm);
        nop.emit(masm);
        nop.emit(masm);
        nop.emit(masm);
        nop.emit(masm);
        nop.emit(masm);
        new SPARCAssembler.Add(dstReg, 0, dstReg).emit(masm);
        // TODO: Fix this issue with the pc relative addressing (This is just my first guess how to
        // do this)
        new SPARCAssembler.Sub(dstReg, 10 * 4, dstReg).emit(masm);
        new SPARCAssembler.Rdpc(SPARC.g5).emit(masm);
        new SPARCAssembler.Add(SPARC.g5, dstReg, dstReg).emit(masm);
    }

}
