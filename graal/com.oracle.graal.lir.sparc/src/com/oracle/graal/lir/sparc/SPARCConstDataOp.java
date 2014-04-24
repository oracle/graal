package com.oracle.graal.lir.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.RawData;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.SPARCMove.LoadAddressOp;
import com.oracle.graal.sparc.*;
import com.sun.javafx.binding.SelectBinding.*;

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
        Register dstReg = ValueUtil.asLongReg(dst);
        long addr = 0; // Will be fixed up by the loader
        new SPARCAssembler.Sethi((int) addr, dstReg).emit(masm);
        new SPARCAssembler.Add(SPARC.g0, (int) addr, dstReg).emit(masm);
    }

}
